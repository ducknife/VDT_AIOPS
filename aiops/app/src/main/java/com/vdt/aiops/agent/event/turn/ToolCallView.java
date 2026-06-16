package com.vdt.aiops.agent.event.turn;

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
public class ToolCallView {
    private String name;
    private Object arguments;
    private String type;
}
