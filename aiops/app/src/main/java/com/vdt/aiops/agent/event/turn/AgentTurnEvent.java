package com.vdt.aiops.agent.event.turn;

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
public class AgentTurnEvent {
    private String investigationId; 
    private int turn; 
    private List<ToolCallView> tools;
}
