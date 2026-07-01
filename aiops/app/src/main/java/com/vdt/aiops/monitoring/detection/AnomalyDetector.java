package com.vdt.aiops.monitoring.detection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.github.dockerjava.api.model.Container;
import com.vdt.aiops.config.properties.AiopsProperties;
import com.vdt.aiops.monitoring.alertmanager.AlertManager;
import com.vdt.aiops.monitoring.detection.enums.AnomalyType;
import com.vdt.aiops.monitoring.metricscraper.MetricCollector;
import com.vdt.aiops.monitoring.metricscraper.MetricsOfService;
import com.vdt.aiops.utils.MonitoredServices;
import com.vdt.aiops.utils.ServiceName;
import com.vdt.aiops.utils.ServiceType;

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
    private final MonitoredServices monitoredServices;

    @Scheduled(fixedDelayString = "${aiops.monitoring.poll-interval-ms}")
    public void scan() {
        List<DetectedAnomaly> detectedAnomalies = new ArrayList<>();
        Set<String> healthyKeys = new HashSet<>(); // service that is OK
        var rules = aiopsProperties.getAnomaly().getRules();

        for (Container c : monitoredServices.list()) {
            String service = ServiceName.serviceName(c);
            String serviceType = ServiceType.of(service);
            if (serviceType == null)
                continue; // exporter / traffic load gen

            MetricsOfService metricsOfService = metricCollector.collect(service);
            for (AnomalyRule rule : rules) {
                if (rule.getServiceType().equals(serviceType)) {
                    Double value = metricsOfService.getProbes().get(rule.getSignal());
                    if (value == null)
                        continue;
                    if (rule.breached(value)) {
                        AnomalyType type = rule.getType();
                        String message = rule.getSignal() + "=" + String.format("%.2f", value)
                                + (rule.isGreaterThan() ? ">" : "<") + " " + String.format("%.2f", rule.getThreshold());
                        detectedAnomalies.add(DetectedAnomaly.builder()
                                .service(service)
                                .type(type)
                                .message(message)
                                .build());
                    } else {
                        healthyKeys.add(service + " | " + rule.getType());
                    }
                }
            }
        }
        // handle this detected turn
        alertManager.handle(detectedAnomalies, healthyKeys);
    }
}
