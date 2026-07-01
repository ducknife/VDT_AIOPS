package com.vdt.aiops.monitoring.metricscraper;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/*
 * Golden signal of services — key theo LOẠI service (redis/postgres/nginx/node-api).
 * PromQL là TEMPLATE: mỗi selector có {%s} để MetricCollector tiêm bộ lọc instance,
 * ví dụ service="redis-payments" -> redis_up{service="redis-payments"}.
 * Nhờ vậy nhiều instance cùng loại không bị đọc lẫn series (collision).
 */
@Component
public class MetricProfiles {

        private static final Map<String, List<MetricProbe>> BY_SERVICE = Map.of(
                        "redis", List.of(
                                        new MetricProbe("isUp", "redis_up{%s}"),
                                        new MetricProbe("evictedKeysPerSec", "rate(redis_evicted_keys_total{%s}[1m])"),
                                        new MetricProbe("keyspaceMissesPerSec",
                                                        "rate(redis_keyspace_misses_total{%s}[1m])"), // cache-miss storm
                                                                                                      // (hệ quả OOM)
                                        new MetricProbe("memUsedBytes", "redis_memory_used_bytes{%s}"),
                                        new MetricProbe("memMaxBytes", "redis_memory_max_bytes{%s}"),
                                        new MetricProbe("memUsedPercent",
                                                        "redis_memory_used_bytes{%s} / redis_memory_max_bytes{%s} * 100")),
                        "postgres", List.of(
                                        new MetricProbe("isUp", "pg_up{%s}"),
                                        new MetricProbe("activeConnections",
                                                        "sum(pg_stat_activity_count{state=\"active\", %s})"), // active vs
                                                                                                              // total cho
                                                                                                              // exhaustion
                                        new MetricProbe("totalConnections", "sum(pg_stat_activity_count{%s})"),
                                        new MetricProbe("maxConnections", "pg_settings_max_connections{%s}"),
                                        // % connection / max connection
                                        new MetricProbe("connUtilPercent",
                                                        "sum(pg_stat_activity_count{%s}) / scalar(pg_settings_max_connections{%s}) * 100")),
                        "nginx", List.of(
                                        new MetricProbe("isUp", "nginx_up{%s}"),
                                        new MetricProbe("activeConnections", "nginx_connections_active{%s}"),
                                        new MetricProbe("requestRate", "rate(nginx_http_requests_total{%s}[1m])"),
                                        new MetricProbe("waitingClients", "nginx_connections_waiting{%s}")),
                        "node-api", List.of(
                                        new MetricProbe("isUp", "up{%s}"),
                                        new MetricProbe("requestRate", "rate(http_requests_total{%s}[1m])"),
                                        new MetricProbe("errorRate", "rate(http_errors_total{%s}[1m])"),
                                        // P99 latency (ms) from histogram
                                        new MetricProbe("latencyMs",
                                                        "histogram_quantile(0.99, sum(rate(http_request_duration_seconds_bucket{%s}[1m])) by (le)) * 1000")));

        /* Get Specific probes of a service TYPE (redis/postgres/nginx/node-api) */
        public List<MetricProbe> forType(String service) {
                return BY_SERVICE.getOrDefault(service, List.of());
        }
}
