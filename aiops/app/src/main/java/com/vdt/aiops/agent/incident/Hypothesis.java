package com.vdt.aiops.agent.incident;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/* Non-validated Findings */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Hypothesis {
    private String claim;
    private String needsVerification;
}
