package com.vdt.aiops.config.websocket.snapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vdt.aiops.agent.incident.Incident;
import com.vdt.aiops.agent.incident.IncidentRepository;
import com.vdt.aiops.monitoring.alertmanager.Alert;
import com.vdt.aiops.monitoring.alertmanager.AlertRepository;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

/* Builds the initial state pushed to a TUI right after it connects. */
@Component
@RequiredArgsConstructor
public class SnapshotProvider {

        private final IncidentRepository incidentRepository;
        private final AlertRepository alertRepository;
        private final ObjectMapper objectMapper;

        @SneakyThrows
        @Transactional(readOnly = true)
        public String snapshotJson() {
                List<Incident> recent = incidentRepository.findTop10ByOrderByAnalyzedAtDesc();
                List<Long> allIncidentIds = recent.stream().map(Incident::getId).toList();
                // avoid N + 1 Query when each card, get alert of its.
                Map<Long, List<Alert>> alertsByIncidentId = allIncidentIds.isEmpty()
                                ? Map.of()
                                : alertRepository.findByIncidentIdIn(allIncidentIds).stream()
                                                .filter(a -> a.getIncidentId() != null)
                                                .collect(Collectors.groupingBy(Alert::getIncidentId));

                // group incidents by investigationId
                Map<String, List<Incident>> byInv = recent.stream()
                                .collect(Collectors.groupingBy(
                                                inc -> inc.getInvestigationId() != null ? inc.getInvestigationId()
                                                                : "legacy-" + inc.getId(),
                                                LinkedHashMap::new,
                                                Collectors.toList()));

                List<SnapshotCard> cards = new ArrayList<>();
                for (var e : byInv.entrySet()) {
                        List<Incident> incidents = e.getValue();
                        List<Alert> alerts = incidents.stream()
                                        .flatMap(incident -> alertsByIncidentId
                                                        .getOrDefault(incident.getId(), List.of()).stream())
                                        .toList();
                        cards.add(SnapshotCard.builder()
                                        .investigationId(e.getKey())
                                        .incidents(incidents)
                                        .alerts(alerts)
                                        .build());
                }

                return objectMapper.writeValueAsString(
                                Map.of("type", "snapshot",
                                                "data", Map.of("cards", cards)));
        }
}
