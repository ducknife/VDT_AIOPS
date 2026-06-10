package com.vdt.aiops.agent.incident;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/* cited Evidence */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Evidence {
    private String source;
    private String detail;
}
