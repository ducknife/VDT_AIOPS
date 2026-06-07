package com.vdt.aiops.monitoring.metricscraper;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/* Collect all metrics of a service */
@Getter
@AllArgsConstructor
@Builder
public class MetricsOfService {
    private String service;
    private String state;
    private double cpuPercent;
    private double memPercent;
    private double memUsedMb;
    private Map<String, Double> probes; /* specific metrics of a service */
}
