package com.vdt.aiops.agent.event;

import java.util.List;

import com.vdt.aiops.agent.incident.IncidentReport;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/* Fired after an investigation's incidents are persisted. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class IncidentDiagnosedEvent {
    private String investigationId;
    private String rootService;
    private List<IncidentReport> reports;
}
