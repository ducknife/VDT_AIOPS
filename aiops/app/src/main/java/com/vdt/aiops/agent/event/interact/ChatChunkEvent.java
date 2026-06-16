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
public class ChatChunkEvent {
    private String conversationId;
    private String delta; // new tokens that new chunk bring when stream
}
