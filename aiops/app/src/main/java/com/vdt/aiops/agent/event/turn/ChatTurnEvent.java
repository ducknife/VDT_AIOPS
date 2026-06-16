package com.vdt.aiops.agent.event.turn;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatTurnEvent {
    private String conversationId;
    private List<ToolCallView> tools;
}
