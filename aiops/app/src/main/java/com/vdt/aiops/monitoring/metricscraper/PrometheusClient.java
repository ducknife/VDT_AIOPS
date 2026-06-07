package com.vdt.aiops.monitoring.metricscraper;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.vdt.aiops.config.properties.AiopsProperties;

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
}
