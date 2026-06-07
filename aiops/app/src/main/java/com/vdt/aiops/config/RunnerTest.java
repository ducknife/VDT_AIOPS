package com.vdt.aiops.config;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

import com.vdt.aiops.agent.context.ContextBuilder;
import com.vdt.aiops.config.properties.AiopsProperties;
import com.vdt.aiops.monitoring.alertmanager.Alert;
import com.vdt.aiops.monitoring.alertmanager.AlertCorrelator;
import com.vdt.aiops.monitoring.alertmanager.AlertGroup;
import com.vdt.aiops.monitoring.alertmanager.AlertRepository;
import com.vdt.aiops.topology.ServiceGraphBuilder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class RunnerTest implements CommandLineRunner {

    private final AiopsProperties aiopsProperties;
    private final ContextBuilder contextBuilder;
    private final AlertRepository alertRepository;
    private final ServiceGraphBuilder serviceGraphBuilder;
    private final AlertCorrelator alertCorrelator;

    @Override
    public void run(String... args) {
        log.info("____Anomaly size: _______" + String.valueOf(aiopsProperties.getAnomaly().getRules().size()));
        List<Alert> active = alertRepository.findByActiveTrue();
        List<AlertGroup> groups = alertCorrelator.correlate(active);

        log.info("==================== CORRELATION RESULT ====================");
        log.info("Active alerts: {}  ->  Groups: {}", active.size(), groups.size());
        int gi = 0;
        for (AlertGroup group : groups) {
            log.info("------------------------------------------------------------");
            log.info("Group #{}  | rootCandidate = {}  | size = {}",
                    gi++, group.getRootCandidate(), group.getAlerts().size());
            for (Alert a : group.getAlerts()) {
                String marker = a.getService().equals(group.getRootCandidate()) ? " <== ROOT" : "";
                log.info("    - {} | {} | {}{}",
                        a.getService(), a.getType(), a.getMessage(), marker);
            }
        }
        log.info("============================================================");
    }
}
