package com.vdt.aiops.tools.read;

import org.springframework.stereotype.Component;

import com.vdt.aiops.monitoring.metricscraper.PrometheusClient;

import lombok.RequiredArgsConstructor;


/* Agent can query promQL on demand, do it on phase 3  */
@Component
@RequiredArgsConstructor
public class QueryMetrics {
    
    private final PrometheusClient prometheusClient;

}
