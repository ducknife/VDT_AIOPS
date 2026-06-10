package com.vdt.aiops.tools.read;

import java.util.stream.Collectors;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import com.vdt.aiops.monitoring.metricscraper.MetricCollector;
import com.vdt.aiops.monitoring.metricscraper.MetricsOfService;

import lombok.RequiredArgsConstructor;

/* Get Metrics of service */
@Component
@RequiredArgsConstructor
public class GetServiceMetrics {

    private final MetricCollector metricCollector;

    @Tool(description = """
                Get current health and resource metrics for ONE monitored service. Call this when \
                investigating a service suspected of being slow, erroring, unhealthy or resource-starved, \
                or to confirm whether a service is behaving normally.
                Always returned (live Docker stats):
                - state: container state (running, exited, ...)
                - cpuPercent, memPercent, memUsedMb: live resource usage
                Plus service-specific signals from Prometheus (vary by service):
                - redis: isUp, evictedKeysPerSec (eviction = memory pressure / OOM), memUsedMiB, memMaxMiB, memUsedPercent
                - postgres: isUp, totalConnections, maxConnections (connection-pool exhaustion)
                - nginx: activeConnections, requestRate, waitingClients
                - node-api: requestRate, errorRate, latencyMs (HTTP traffic health)
                A field shown as 'n/a' means that metric is not collected for this service.
                Parameter 'service': the service name to inspect, e.g. 'node-api', 'redis', 'postgres', 'nginx'. \
                If you need to discover which related services to inspect, call GetServiceDependencies first.
            """)
    public String fetchMetrics(String service) {
        MetricsOfService metricOfService = metricCollector.collect(service);
        if (metricOfService == null) {
            return "Service: " + service + " " + "not found or stats unavailable.";
        }
        String probeStr = metricOfService.getProbes().isEmpty()
                ? "(no service-specific metrics)"
                : metricOfService.getProbes().entrySet().stream()
                        .map(e -> formatProbe(e.getKey(), e.getValue()))
                        .collect(Collectors.joining(", "));

        return String.format(
                "Service: %s [%s]%n"
                        + "cpuPercent=%.1f%%, memPercent=%.1f%% (%.0f MiB used)%n"
                        + "%s",
                metricOfService.getService(), metricOfService.getState(), metricOfService.getCpuPercent(),
                metricOfService.getMemPercent(), metricOfService.getMemUsedMb(), probeStr);
    }

    /*
        Format
     */
    private String formatProbe(String key, Double value) {
        if (value == null) {
            return key + "=n/a";
        }
        if (key.endsWith("Bytes")) {
            String label = key.substring(0, key.length() - "Bytes".length()) + "MiB";
            return label + "=" + String.format("%.1f", value / 1024.0 / 1024.0);
        }
        if (key.endsWith("Percent")) {
            return key + "=" + String.format("%.2f%%", value);
        }
        return key + "=" + String.format("%.3f", value);
    }
}
