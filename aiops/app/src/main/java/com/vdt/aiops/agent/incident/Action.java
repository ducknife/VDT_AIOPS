package com.vdt.aiops.agent.incident;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/* Recommened Action */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Action {
    private String action;
    private String why;
}
