package com.vdt.aiops.agent.event.start;

import java.util.List;

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
public class InvestigationStartedEvent {
    private String investigationId; 
    private String rootService; 
    private List<AlertView> alerts;
}
