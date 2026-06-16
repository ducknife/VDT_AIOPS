package com.vdt.aiops.config.websocket;

import java.time.Instant;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vdt.aiops.agent.event.StatusChangedEvent;
import com.vdt.aiops.agent.incident.Incident;
import com.vdt.aiops.agent.incident.IncidentRepository;
import com.vdt.aiops.agent.incident.enums.IncidentStatus;
import com.vdt.aiops.monitoring.alertmanager.Alert;
import com.vdt.aiops.monitoring.alertmanager.AlertRepository;

import lombok.RequiredArgsConstructor;


/* handle when operator click ack or resolve */
@Service
@Transactional
@RequiredArgsConstructor
public class TuiCommandService {

    private final IncidentRepository incidentRepository;
    private final AlertRepository alertRepository;
    private final ApplicationEventPublisher eventPublisher;

    public void ack(Long incidentId) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new RuntimeException("Incident Not Found!"));
        incident.setStatus(IncidentStatus.ACKNOWLEDGED);
        incidentRepository.save(incident);
        eventPublisher.publishEvent(
                new StatusChangedEvent(incidentId, incident.getStatus()));
    }

    public void resolve(Long incidentId) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new RuntimeException("Incident Not Found!"));
        incident.setStatus(IncidentStatus.RESOLVED);
        List<Alert> alerts = alertRepository.findByIncidentId(incidentId);
        alerts.stream().forEach(a -> {
            a.setActive(false);
            a.setResolvedAt(Instant.now());
        });
        incidentRepository.save(incident);
        alertRepository.saveAll(alerts);
        eventPublisher.publishEvent(
            new StatusChangedEvent(incidentId, incident.getStatus())
        );
    }
}
