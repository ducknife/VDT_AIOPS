package com.vdt.aiops.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.vdt.aiops.agent.incident.Incident;
import com.vdt.aiops.agent.incident.IncidentReport;
import com.vdt.aiops.agent.incident.IncidentRepository;
import com.vdt.aiops.monitoring.alertmanager.Alert;
import com.vdt.aiops.monitoring.alertmanager.AlertRepository;
import com.vdt.aiops.monitoring.alertmanager.enums.AlertStatus;

import lombok.RequiredArgsConstructor;

/*
    Map incident report => incident and link it to alerts
    Mark FAILED if an alert max attempts but still failed.
 */
@Component
@RequiredArgsConstructor
public class SaveIncidentReport {

    private final IncidentRepository incidentRepository;
    private final AlertRepository alertRepository;

    @Transactional
    public void persist(List<IncidentReport> reports, List<Alert> groupAlerts, long investigationMs,
        String investigationId
    ) {
        // map alertId -> incidentId
        Map<Long, Long> alertIdToIncId = new HashMap<>();
        List<Long> groupAlertIds = groupAlerts.stream().map(a -> a.getId()).toList();
        for (IncidentReport report : reports) {
            Incident incident = from(report);
            incident.setInvestigationMs(investigationMs);
            incident.setInvestigationId(investigationId);
            Incident saved = incidentRepository.save(incident);
            List<Long> covered = report.getCoveredAlertIds();
            if (covered != null && !covered.isEmpty()) {
                for (Long alertId : covered) {
                    if (groupAlertIds.contains(alertId)) {
                        alertIdToIncId.put(alertId, saved.getId());
                    }
                }
            }
        }

        for (Alert a : groupAlerts) {
            a.setStatus(AlertStatus.DIAGNOSED);
            a.setIncidentId(alertIdToIncId.get(a.getId()));
        }
        alertRepository.saveAll(groupAlerts);
    }

    @Transactional
    public void markFailure(List<Long> alertIds, int maxAttempts) {
        List<Alert> alerts = alertRepository.findAllByIdIn(alertIds);
        for (Alert a : alerts) {
            int next = a.getInvestigationAttempts() + 1;
            a.setInvestigationAttempts(next);
            a.setStatus(next >= maxAttempts ? AlertStatus.FAILED : AlertStatus.ACTIVE);
        }
        alertRepository.saveAll(alerts);
    }

    private Incident from(IncidentReport report) {
        return Incident.builder()
                .service(report.getService())
                .title(report.getTitle())
                .severity(report.getSeverity())
                .summary(report.getSummary())
                .rootCause(report.getRootCause())
                .validatedFindings(report.getValidatedFindings())
                .hypotheses(report.getHypotheses())
                .recommendedActions(report.getRecommendedActions())
                .citedEvidence(report.getCitedEvidence())
                .build();
    }
}
