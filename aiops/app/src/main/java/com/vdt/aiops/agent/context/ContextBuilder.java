package com.vdt.aiops.agent.context;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.vdt.aiops.config.properties.AiopsProperties;
import com.vdt.aiops.monitoring.alertmanager.AlertGroup;
import com.vdt.aiops.monitoring.logcollector.Log;
import com.vdt.aiops.monitoring.logcollector.LogGroup;
import com.vdt.aiops.monitoring.logcollector.LogRepository;
import com.vdt.aiops.monitoring.logcollector.LogTemplateExtractor;
import com.vdt.aiops.monitoring.metricscraper.MetricCollector;
import com.vdt.aiops.monitoring.metricscraper.MetricsOfService;
import com.vdt.aiops.topology.ServiceGraph;
import com.vdt.aiops.topology.ServiceGraphBuilder;
import com.vdt.aiops.topology.enums.ServiceRole;
import com.vdt.aiops.utils.AlertUtils;

import lombok.RequiredArgsConstructor;

/* 
Build context from alerts, logs +- 5 minutes, metrics at same time, graph of service.
 */
@Service
@RequiredArgsConstructor
public class ContextBuilder {

        private final AiopsProperties aiopsProperties;
        private final ServiceGraphBuilder serviceGraphBuilder;
        private final MetricCollector metricCollector;
        private final LogRepository logRepository;
        private final LogTemplateExtractor logTemplateExtractor;

        public ContextBundle build(AlertGroup group) {
                // WINDOW METADATA
                Long win = aiopsProperties.getContext().getLogWindowMinutes();
                Instant from = AlertUtils.earliest(group.getAlerts()).minus(win, ChronoUnit.MINUTES);
                Instant to = AlertUtils.latest(group.getAlerts()).plus(win, ChronoUnit.MINUTES);

                // GRAPH + SCOPE
                ServiceGraph serviceGraph = serviceGraphBuilder.getGraph();
                String focus = group.getRootCandidate();
                Set<String> down = serviceGraph.downstream(focus);
                Set<String> scope = new LinkedHashSet<>();
                group.getAlerts().forEach(a -> scope.add(a.getService()));
                scope.addAll(down);

                // GRAPH VIEW (map service -> role)
                Map<String, ServiceRole> downstream = down.stream()
                                .collect(Collectors.toMap(
                                                d -> d,
                                                d -> serviceGraph.role(d)));

                Map<String, ServiceRole> upstream = serviceGraph.upstream(focus).stream()
                                .collect(Collectors.toMap(
                                                u -> u,
                                                u -> serviceGraph.role(u)));

                // METRICS SEED
                Map<String, MetricsOfService> metrics = scope.stream()
                                .collect(Collectors.toMap(
                                                s -> s,
                                                s -> metricCollector.collect(s)));

                // LOGS curate (group)
                Map<String, List<LogGroup>> logs = scope.stream()
                                .collect(Collectors.toMap(
                                                s -> s,
                                                s -> curate(s, from, to)));

                return ContextBundle.builder()
                                .focus(focus)
                                .alerts(group.getAlerts())
                                .windowFrom(from)
                                .windowTo(to)
                                .focusRole(serviceGraph.role(focus))
                                .downstream(downstream)
                                .upstream(upstream)
                                .metrics(metrics)
                                .logs(logs)
                                .build();

        }

        // Group logs together
        private List<LogGroup> curate(String service, Instant from, Instant to) {
                List<Log> logs = logRepository.findByServiceAndLoggedAtBetween(service, from, to);
                // Group by level and template
                Map<String, List<Log>> byKey = logs.stream()
                                .collect(Collectors.groupingBy(l -> l.getLogLevel().name() + " | "
                                                + logTemplateExtractor.templateOf(l.getMessage())));

                // Each group, pass it to LogGroup
                List<LogGroup> groups = byKey.values().stream()
                                .map(g -> LogGroup.builder()
                                                .level(g.get(0).getLogLevel())
                                                .pattern(logTemplateExtractor.templateOf(g.get(0).getMessage()))
                                                .count(1L * g.size())
                                                .raw(g.get(0).getMessage())
                                                .firstAt(g.stream().map(l -> l.getLoggedAt())
                                                                .min(Comparator.naturalOrder()).orElse(null))
                                                .lastAt(g.stream().map(l -> l.getLoggedAt())
                                                                .max(Comparator.naturalOrder()).orElse(null))
                                                .build())
                                .collect(Collectors.toList());
                return groups;
        }

}
