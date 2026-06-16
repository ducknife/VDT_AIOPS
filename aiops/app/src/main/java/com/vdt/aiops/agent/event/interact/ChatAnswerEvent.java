package com.vdt.aiops.agent.event.interact;

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
public class ChatAnswerEvent {
    private String conversationId;
    private String text;
}
