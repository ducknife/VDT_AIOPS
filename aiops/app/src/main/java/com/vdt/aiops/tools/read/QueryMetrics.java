package com.vdt.aiops.tools.read;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import com.vdt.aiops.monitoring.metricscraper.PrometheusClient;
import com.vdt.aiops.monitoring.metricscraper.PrometheusResponse.Result;

import lombok.RequiredArgsConstructor;

/* Agent can query PromQL over a time RANGE on demand (phase 3, forensic) */
@Component
@RequiredArgsConstructor
public class QueryMetrics {

    private final PrometheusClient prometheusClient;

    @Tool(description = """
            Query HISTORICAL metrics from Prometheus over a time RANGE (not just the current value). \
            Use this to see how a metric behaved DURING the incident window - e.g. a latency or \
            error-rate spike that may have already recovered. ALWAYS query AROUND the incident time \
            (use the event window from the context), not the current time, because the problem may be gone now.
            Parameters:
            - query: a PromQL expression. Examples: \
              rate(http_requests_total[1m]) and rate(http_errors_total[1m]) for node-api traffic/errors; \
              histogram_quantile(0.99, rate(http_request_duration_seconds_bucket[1m])) for P99 latency; \
              redis_memory_used_bytes; pg_stat_activity_count.
            - fromIso, toIso: ISO-8601 timestamps bounding the range, e.g. 2024-01-01T10:00:00Z.
            - stepSeconds: resolution in seconds (optional, default 15).
            Returns, per series: its labels and a summary (point count, min, max, avg, last value).
            """)
    public String queryRange(String query, String fromIso, String toIso, Integer stepSeconds) {
        try {
            Instant from = Instant.parse(fromIso);
            Instant to = Instant.parse(toIso);
            long step = (stepSeconds == null || stepSeconds <= 0) ? 15 : stepSeconds;

            List<Result> series = prometheusClient.queryRange(query, from, to, step);
            if (series == null || series.isEmpty()) {
                return "No data for query: " + query;
            }
            StringBuilder sb = new StringBuilder(
                    "PromQL: " + query + "  (" + fromIso + " -> " + toIso + ", step " + step + "s)\n");
            for (Result r : series) {
                sb.append(formatSeries(r)).append("\n");
            }
            return sb.toString().trim();
        } catch (DateTimeParseException e) {
            return "Invalid timestamp format. Use ISO-8601, e.g. 2024-01-01T10:00:00Z";
        }
    }

    /* Summarize one series (min/max/avg/last) instead of dumping every point */
    private String formatSeries(Result r) {
        List<List<Object>> values = r.getValues();
        if (values == null || values.isEmpty()) {
            return "(empty series)";
        }
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE, sum = 0, last = 0;
        int n = 0;
        for (List<Object> point : values) {
            try {
                double v = Double.parseDouble(point.get(1).toString());
                min = Math.min(min, v);
                max = Math.max(max, v);
                sum += v;
                last = v;
                n++;
            } catch (NumberFormatException ignore) {
                /* skip non-numeric (NaN) point */
            }
        }
        if (n == 0) {
            return "(no numeric points)";
        }
        String labels = r.getMetric() == null ? "" : r.getMetric().toString();
        return String.format("%s  points=%d min=%.3f max=%.3f avg=%.3f last=%.3f",
                labels, n, min, max, sum / n, last);
    }
}
