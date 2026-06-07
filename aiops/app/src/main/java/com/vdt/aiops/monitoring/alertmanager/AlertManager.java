package com.vdt.aiops.monitoring.alertmanager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vdt.aiops.monitoring.detection.DetectedAnomaly;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AlertManager {

    private final AlertRepository alertRepository;

    @Transactional
    public void handle(List<DetectedAnomaly> detectedAnomalies, Set<String> healthyKeys) {

        // Step 1: create + dedub alerts
        for (DetectedAnomaly d : detectedAnomalies) {
            Optional<Alert> existing = alertRepository.findByServiceAndTypeAndActiveTrue(d.getService(), d.getType());
            // if this is a new alert, else don't save because of dedup
            if (existing.isEmpty()) {
                Alert alert = Alert.builder()
                        .service(d.getService())
                        .type(d.getType())
                        .message(d.getMessage())
                        .build();
                alertRepository.save(alert);
                // TODO: agent will start investigate here
            }
        }

        // Step 2: resolve only if measured HEALTHY
        // if it's not in keys set, is active = false
        for (Alert openAlert : alertRepository.findByActiveTrue()) {
            String key = openAlert.getService() + " | " + openAlert.getType();
            if (healthyKeys.contains(key)) {
                openAlert.setResolvedAt(Instant.now());
                openAlert.setActive(false);
                alertRepository.save(openAlert);
            }
        }
    }
}
