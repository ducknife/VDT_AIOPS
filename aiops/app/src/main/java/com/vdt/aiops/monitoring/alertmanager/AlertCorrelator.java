package com.vdt.aiops.monitoring.alertmanager;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.vdt.aiops.topology.ServiceGraph;
import com.vdt.aiops.topology.ServiceGraphBuilder;

import lombok.RequiredArgsConstructor;

/*
 * Correlates active alerts into AlertGroups (one incident-candidate per cluster) and picks a
 * topological rootCandidate (the service most others depend on) per group.
 *
 * DESIGN TRADE-OFF — intentionally COARSE grouping:
 * Alerts are linked by service sameness/adjacency (undirected) + time window, then merged via
 * connected components. This can OVER-MERGE: two independent incidents sharing a victim service
 * (e.g. postgres-down AND redis-OOM both surfacing as node-api alerts) get bridged into one cluster,
 * and rootCandidate may then be ambiguous.
 *
 * Accepted because topology ALONE cannot attribute a shared victim's symptoms to the right cause —
 * that needs log/metric evidence. Over-merging is cheap and safe (just batches one investigation);
 * under-merging would fragment a single incident into redundant investigations.
 *
 * The authoritative split happens in the SEMANTIC layer: the agent investigates an AlertGroup and
 * returns List<Incident>, separating it into the correct distinct incidents (each with its own root
 * cause / severity / remediation). So an AlertGroup is a PRELIMINARY grouping for dispatch and the
 * "detected, analyzing..." view; the agent's Incident(s) are the final, possibly-split result.
 */
@Component
@RequiredArgsConstructor
public class AlertCorrelator {

    private final ServiceGraphBuilder serviceGraphBuilder;
    // 5 minutes to collect alert in same window time
    private static final Duration WINDOW = Duration.ofMinutes(5);

    public List<AlertGroup> correlate(List<Alert> active) {
        int n = active.size();
        ServiceGraph graph = serviceGraphBuilder.getGraph();

        int[] parent = new int[n]; // parent[i] = parent of alert i th
        for (int i = 0; i < n; i++) {
            parent[i] = i; // init parent[i] = i;
        }

        // union
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (related(active.get(i), active.get(j), graph)) {
                    union(parent, i, j);
                }
            }
        }

        // group alerts by root
        Map<Integer, List<Alert>> clusters = new HashMap<>();
        for (int i = 0; i < n; i++) {
            clusters.computeIfAbsent(find(parent, i), k -> new ArrayList<>())
                    .add(active.get(i));
        }

        // find root of a group
        return clusters.values().stream()
                .map(alerts -> AlertGroup.builder()
                        .alerts(alerts)
                        .rootCandidate(rootOf(alerts, graph))
                        .build())
                .collect(Collectors.toList());

    }

    // check if 2 alerts related by same of adjacent
    private boolean related(Alert a, Alert b, ServiceGraph graph) {
        String sa = a.getService(), sb = b.getService();
        boolean sameOrAdjacent = sa.equals(sb) || graph.downstream(sa).contains(sb)
                || graph.upstream(sa).contains(sb);
        boolean timeClose = Duration.between(a.getDetectedAt(), b.getDetectedAt()).abs().compareTo(WINDOW) <= 0;
        return sameOrAdjacent && timeClose; // must at same window time and adjacent
    }

    // find root of group contains x
    private int find(int[] parent, int x) {
        if (x == parent[x])
            return x;
        else
            return parent[x] = find(parent, parent[x]);
    }

    // union if can
    private void union(int[] parent, int x, int y) {
        x = find(parent, x);
        y = find(parent, y);
        if (x == y)
            return;
        if (x > y) {
            int tmp = x;
            x = y;
            y = tmp;
        }
        parent[y] = x;
    }

    // find root of a alert group, in group, if service has max service depended on
    // its.
    private String rootOf(List<Alert> alerts, ServiceGraph graph) {
        Set<String> services = alerts.stream().map(a -> a.getService())
                .collect(Collectors.toSet());
        return services.stream()
                .max(Comparator.comparingInt(
                        s -> (int) graph.upstream(s).stream().filter(service -> services.contains(service)).count()))
                .orElse(null);
    }
}
