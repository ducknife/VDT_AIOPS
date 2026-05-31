package com.vdt.aiops.tools.read;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import com.vdt.aiops.monitoring.metricscraper.MetricSnapshot;
import com.vdt.aiops.monitoring.metricscraper.MetricSnapshotRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class GetContainerMetrics {

    private final MetricSnapshotRepository metricSnapshotRepository;

    @Tool(description = "Fetch metrics snapshots (CPU%, memory%, request rate, error rate, latency ms, health status) "
            + "of a container between two timestamps. " +
            "Use this to understand resource usage and traffic patterns around the time of an anomaly. " +
            "Parameters: container name (e.g. 'aiops-nginx'), fromIso and toIso in ISO-8601 format (e.g. '2024-01-01T10:00:00Z'). "
            + "Returns time-series snapshots scraped every 5 seconds.")
    public String fetchMetrics(String container, String fromIso, String toIso) {
        try {
            Instant from = Instant.parse(fromIso);
            Instant to = Instant.parse(toIso);
            List<MetricSnapshot> metricSnapshots = metricSnapshotRepository
                    .findByContainerAndScrapedAtBetween(container, from, to);
            if (metricSnapshots.isEmpty()) {
                return "No metrics found for container: " + container + " in the given time range.";
            }
            return "Container: " + container + " | Snapshots: " + metricSnapshots.size() + "\n"
                    + metricSnapshots.stream()
                            .map(this::format)
                            .collect(Collectors.joining("\n"));
                    
        } catch (DateTimeParseException e) {
            return "Invalid timestamp format. Use ISO-8601, e.g. 2024-01-01T10:00:00Z";
        }
    }

    private String format(MetricSnapshot s) {
        return String.format("[%s] cpu=%.1f%% mem=%.1f%%(%dMB) req=%.1f/s err=%.1f/s latency=%.0fms healthy=%s",
                s.getScrapedAt(),
                s.getCpuPercent() != null ? s.getCpuPercent() : 0.0,
                s.getMemPercent() != null ? s.getMemPercent() : 0.0,
                s.getMemUsedBytes() != null ? s.getMemUsedBytes() / 1_048_576 : 0,
                s.getRequestRate() != null ? s.getRequestRate() : 0.0,
                s.getErrorRate() != null ? s.getErrorRate() : 0.0,
                s.getLatencyMs() != null ? s.getLatencyMs() : 0.0,
                Boolean.TRUE.equals(s.getHealthy()) ? "true" : "false");
    }
}
