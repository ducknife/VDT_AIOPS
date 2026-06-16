package com.vdt.aiops.agent.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InvestigationFailedEvent {
    private String investigationId;
    private String rootService; 
    private String reason;
}
