package com.vdt.aiops.monitoring.metricscraper;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/* Golden signal of services */
@Component
public class MetricProfiles {

        private static final Map<String, List<MetricProbe>> BY_SERVICE = Map.of(
                        "redis", List.of(
                                        new MetricProbe("isUp", "redis_up"),
                                        new MetricProbe("evictedKeysPerSec", "rate(redis_evicted_keys_total[1m])"),
                                        new MetricProbe("keyspaceMissesPerSec",
                                                        "rate(redis_keyspace_misses_total[1m])"), // cache-miss storm
                                                                                                  // (hệ quả OOM)
                                        new MetricProbe("memUsedBytes", "redis_memory_used_bytes"),
                                        new MetricProbe("memMaxBytes", "redis_memory_max_bytes"),
                                        new MetricProbe("memUsedPercent",
                                                        "redis_memory_used_bytes / redis_memory_max_bytes * 100")),
                        "postgres", List.of(
                                        new MetricProbe("isUp", "pg_up"),
                                        new MetricProbe("activeConnections",
                                                        "sum(pg_stat_activity_count{state=\"active\"})"), // active vs
                                                                                                          // total cho
                                                                                                          // exhaustion
                                        new MetricProbe("totalConnections", "sum(pg_stat_activity_count)"),
                                        new MetricProbe("maxConnections", "pg_settings_max_connections"),
                                        // % connection / max connection
                                        new MetricProbe("connUtilPercent",
                                                        "sum(pg_stat_activity_count) / scalar(pg_settings_max_connections) * 100")),
                        "nginx", List.of(
                                        new MetricProbe("isUp", "nginx_up"),
                                        new MetricProbe("activeConnections", "nginx_connections_active"),
                                        new MetricProbe("requestRate", "rate(nginx_http_requests_total[1m])"),
                                        new MetricProbe("waitingClients", "nginx_connections_waiting")),
                        "node-api", List.of(
                                        new MetricProbe("isUp", "up{job=\"node-api\"}"),
                                        new MetricProbe("requestRate", "rate(http_requests_total[1m])"),
                                        new MetricProbe("errorRate", "rate(http_errors_total[1m])"),
                                        // P99 latency (ms) from histogram
                                        new MetricProbe("latencyMs",
                                                        "histogram_quantile(0.99, sum(rate(http_request_duration_seconds_bucket[1m])) by (le)) * 1000")));

        /* Get Specific probes of service */
        public List<MetricProbe> forService(String service) {
                return BY_SERVICE.getOrDefault(service, List.of());
        }
}
