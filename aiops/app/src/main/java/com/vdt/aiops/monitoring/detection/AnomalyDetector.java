package com.vdt.aiops.monitoring.detection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.vdt.aiops.config.properties.AiopsProperties;
import com.vdt.aiops.monitoring.alertmanager.AlertManager;
import com.vdt.aiops.monitoring.detection.enums.AnomalyType;
import com.vdt.aiops.monitoring.metricscraper.MetricCollector;
import com.vdt.aiops.monitoring.metricscraper.MetricsOfService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/* Detect Anomaly by scraping the metrics every 5 seconds */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnomalyDetector {

    private final AiopsProperties aiopsProperties;
    private final MetricCollector metricCollector;
    private final AlertManager alertManager;

    @Scheduled(fixedDelayString = "${aiops.monitoring.poll-interval-ms}")
    public void scan() {
        List<DetectedAnomaly> detectedAnomalies = new ArrayList<>();
        Set<String> healthyKeys = new HashSet<>(); // service that is OK
        List<String> services = aiopsProperties.getAnomaly().getRules().stream()
                .map(AnomalyRule::getService)
                .distinct()
                .collect(Collectors.toList());
        for (String service : services) {
            MetricsOfService metricsOfService = metricCollector.collect(service);
            for (AnomalyRule rule : aiopsProperties.getAnomaly().getRules()) {
                if (rule.getService().equals(service)) {
                    Double value = metricsOfService.getProbes().get(rule.getSignal());
                    if (value == null)
                        continue;
                    if (rule.breached(value)) {
                        // create DetectedAnomaly(service, type, message)
                        AnomalyType type = rule.getType();
                        String message = rule.getSignal() + "=" + String.format("%.2f", value)
                                + (rule.isGreaterThan() ? ">" : "<") + " " + String.format("%.2f", rule.getThreshold());
                        detectedAnomalies.add(DetectedAnomaly.builder()
                                .service(service)
                                .type(type)
                                .message(message)
                                .build());
                    }
                    else {
                        healthyKeys.add(rule.getService() + " | " + rule.getType());
                    }
                }
            }
        }
        // handle this detected turn
        alertManager.handle(detectedAnomalies, healthyKeys);
    }
}
