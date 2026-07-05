package com.vdt.aiops.monitoring.alertmanager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.vdt.aiops.topology.ServiceGraph;
import com.vdt.aiops.topology.ServiceGraphBuilder;
import com.vdt.aiops.topology.ServiceNode;
import com.vdt.aiops.topology.enums.ServiceRole;

/**
 * Unit tests for the Union-Find alert correlation: grouping by service
 * sameness/adjacency within a 5-minute window, and topological root selection.
 */
class AlertCorrelatorTest {

    private static final Instant T0 = Instant.parse("2026-07-04T02:00:00Z");

    private AlertCorrelator correlator;

    // topology:  nginx -> {node-api, node-api-orders};  node-api -> {postgres, redis};  node-api-orders -> {postgres}
    private ServiceGraph graph() {
        Map<String, Set<String>> ds = new HashMap<>();
        ds.put("nginx", Set.of("node-api", "node-api-orders"));
        ds.put("node-api", Set.of("postgres", "redis"));
        ds.put("node-api-orders", Set.of("postgres"));
        Map<String, ServiceNode> nodes = new HashMap<>();
        for (String s : List.of("nginx", "node-api", "node-api-orders", "postgres", "redis")) {
            nodes.put(s, new ServiceNode(s, "id-" + s, ServiceRole.UNKNOWN));
        }
        return new ServiceGraph(nodes, ds);
    }

    private Alert alert(String service, Instant at) {
        return Alert.builder().service(service).detectedAt(at).build();
    }

    @BeforeEach
    void setUp() {
        ServiceGraphBuilder builder = mock(ServiceGraphBuilder.class);
        when(builder.getGraph()).thenReturn(graph());
        correlator = new AlertCorrelator(builder);
    }

    @Test
    void alertsOnSameServiceAreGrouped() {
        List<AlertGroup> groups = correlator.correlate(List.of(
                alert("node-api", T0),
                alert("node-api", T0.plusSeconds(30))));
        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).getAlerts()).hasSize(2);
    }

    @Test
    void adjacentServicesAreGrouped() {
        // node-api depends on postgres -> adjacent edge -> same group
        List<AlertGroup> groups = correlator.correlate(List.of(
                alert("node-api", T0),
                alert("postgres", T0.plusSeconds(30))));
        assertThat(groups).hasSize(1);
    }

    @Test
    void unrelatedServicesStaySeparate() {
        // redis and node-api-orders share no edge -> two groups
        List<AlertGroup> groups = correlator.correlate(List.of(
                alert("redis", T0),
                alert("node-api-orders", T0.plusSeconds(30))));
        assertThat(groups).hasSize(2);
    }

    @Test
    void adjacentButOutsideTimeWindowNotGrouped() {
        // same service, but 10 minutes apart -> beyond the 5-minute window
        List<AlertGroup> groups = correlator.correlate(List.of(
                alert("node-api", T0),
                alert("node-api", T0.plus(Duration.ofMinutes(10)))));
        assertThat(groups).hasSize(2);
    }

    @Test
    void transitivelyConnectedAlertsFormOneGroupWithSharedRoot() {
        // node-api ~ postgres, node-api-orders ~ postgres (node-api & node-api-orders not directly
        // related) -> all three connect through postgres -> one component.
        List<AlertGroup> groups = correlator.correlate(List.of(
                alert("node-api", T0),
                alert("node-api-orders", T0.plusSeconds(10)),
                alert("postgres", T0.plusSeconds(20))));

        assertThat(groups).hasSize(1);
        AlertGroup g = groups.get(0);
        assertThat(g.getAlerts()).hasSize(3);
        // postgres is depended on by two group members -> highest in-group upstream count -> root
        assertThat(g.getRootCandidate()).isEqualTo("postgres");
    }

    @Test
    void emptyInputProducesNoGroups() {
        assertThat(correlator.correlate(List.of())).isEmpty();
    }
}
