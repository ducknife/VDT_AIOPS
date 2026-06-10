package com.vdt.aiops.monitoring.metricscraper;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.vdt.aiops.config.properties.AiopsProperties;
import com.vdt.aiops.monitoring.metricscraper.PrometheusResponse.Result;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/* Instant Query metrics from Prometheus */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrometheusClient {

    private final RestClient restClient;
    private final AiopsProperties aiopsProperties;

    public Double query(String promQL) {
        try {
            PrometheusResponse response = restClient.get()
                    .uri(aiopsProperties.getMonitoring().getPrometheusUrl()
                            + "/api/v1/query?query={promQL}", promQL)
                    .retrieve() /* send http request and prepare to read response */
                    .body(PrometheusResponse.class); /* Jackson parse to PrometheusResponse */
            if (response == null || response.getData() == null
                    || response.getData().getResult() == null || response.getData().getResult().isEmpty()) {
                return null;
            }
            return Double.parseDouble(
                    response.getData()
                            .getResult()
                            .get(0)
                            .getValue()
                            .get(1)
                            .toString());
        } catch (Exception e) {
            log.warn("Prometheus query failed: {} ({})", promQL, e.getMessage());
            return null; /* failed to fetch */
        }
    }

    /* Range query: returns the matrix of series (each with labels + values over time) */
    public List<Result> queryRange(String promQL, Instant start, Instant end, long stepSeconds) {
        try {
            PrometheusResponse response = restClient.get()
                    .uri(aiopsProperties.getMonitoring().getPrometheusUrl()
                            + "/api/v1/query_range?query={q}&start={s}&end={e}&step={step}",
                            promQL, start.getEpochSecond(), end.getEpochSecond(), stepSeconds)
                    .retrieve()
                    .body(PrometheusResponse.class);
            if (response == null || response.getData() == null || response.getData().getResult() == null) {
                return List.of();
            }
            return response.getData().getResult();
        } catch (Exception e) {
            log.warn("Prometheus query_range failed: {} ({})", promQL, e.getMessage());
            return List.of();
        }
    }
}
