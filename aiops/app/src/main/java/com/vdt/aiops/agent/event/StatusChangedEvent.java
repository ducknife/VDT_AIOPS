package com.vdt.aiops.agent.event;

import com.vdt.aiops.agent.incident.enums.IncidentStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusChangedEvent {
    private Long incidentId;
    private IncidentStatus status;
}
