package com.vdt.aiops.monitoring.alertmanager;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.vdt.aiops.agent.AgentDispatcher;
import com.vdt.aiops.config.properties.AiopsProperties;
import com.vdt.aiops.monitoring.alertmanager.enums.AlertStatus;
import com.vdt.aiops.utils.AlertUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/* correlate periodically
and debounce when group the alerts  */
@Slf4j
@Component
@RequiredArgsConstructor
public class CorrelationTick {

    private final AlertRepository alertRepository;
    private final AlertCorrelator alertCorrelator;
    private final AiopsProperties aiopsProperties;
    private final AgentDispatcher agentDispatcher;

    /* the alert come and group its if can periodically */
    @Scheduled(fixedDelayString = "${aiops.monitoring.poll-interval-ms}")
    @Transactional
    public void dispatchTick() {
        List<Alert> alerts = alertRepository.findByActiveTrueAndStatus(AlertStatus.ACTIVE);
        List<AlertGroup> groups = alertCorrelator.correlate(alerts);
        Instant now = Instant.now();

        for (AlertGroup group : groups) {
            if (shouldDispatch(group, now)) {
                dispatch(group);
            }
        }

    }

    private void dispatch(AlertGroup group) {
        
        // mark all alert in group to INVESTIGATING
        group.getAlerts().forEach(a -> a.setStatus(AlertStatus.INVESTIGATING));
        alertRepository.saveAll(group.getAlerts());
        agentDispatcher.investigate(group); // transactional dispatchTick doesn't be block
    }

    private boolean shouldDispatch(AlertGroup group, Instant now) {
        int quietSecond = aiopsProperties.getCorrelation().getQuietWindowSeconds();
        int maxWaitSecond = aiopsProperties.getCorrelation().getMaxWaitSeconds();

        Instant createdAt = AlertUtils.earliest(group.getAlerts()); 
        Instant updatedAt = AlertUtils.latest(group.getAlerts());

        Duration quiet = Duration.ofSeconds(quietSecond);
        Duration maxWait = Duration.ofSeconds(maxWaitSecond);

        boolean settled = Duration.between(updatedAt, now).compareTo(quiet) >= 0;
        boolean capped = Duration.between(createdAt, now).compareTo(maxWait) >= 0;

        return settled || capped;
    }
}
