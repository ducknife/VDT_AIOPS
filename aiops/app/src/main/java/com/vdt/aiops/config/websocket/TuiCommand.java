package com.vdt.aiops.config.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/* Command to change status of incident */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TuiCommand {
    private String command;
    private Long incidentId;
    private String text;
    private String conversationId;
}
