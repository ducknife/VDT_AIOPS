package com.vdt.aiops.monitoring.detection;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
        Queue<DetectedAnomaly> detectedAnomalies = new ConcurrentLinkedQueue<>();
        Set<String> healthyKeys = ConcurrentHashMap.newKeySet(); // service that is OK
        var rules = aiopsProperties.getAnomaly().getRules();

        // each service -> one thread
        List<Container> containers = monitoredServices.list();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<Void>> tasks = containers.stream()
                    .map(c -> (Callable<Void>) () -> {
                        scanOne(c, rules, detectedAnomalies, healthyKeys);
                        return null;
                    })
                    .toList();
            pool.invokeAll(tasks);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        // handle this detected turn
        alertManager.handle(new ArrayList<>(detectedAnomalies), healthyKeys);
    }

    // for each service
    private void scanOne(Container c, List<AnomalyRule> rules, Queue<DetectedAnomaly> detectedAnomalies,
            Set<String> healthyKeys) {
        String service = ServiceName.serviceName(c);
        String serviceType = ServiceType.of(service);
        try {
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
        } catch (Exception e) {
            log.warn("scanOne failed service={}: {}", service, e.getMessage());
        }
    }
}
