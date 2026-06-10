package com.vdt.aiops.agent.incident;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/* validated Findings: finding and has evidence */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Finding {
    private String finding;
    private String evidence;
}
