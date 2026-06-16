package com.vdt.aiops.config.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/* TUI message format send to TUI */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TuiMessage {
    private String type; 
    private String investigationId;
    private Object data;
}
