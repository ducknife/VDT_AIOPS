package com.vdt.aiops.agent.incident;

import java.util.List;

import com.vdt.aiops.agent.incident.enums.Severity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/* DTO Agent return */
@Getter
@Setter
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class IncidentReport {
    private String service;
    private String title;
    private Severity severity;
    private String summary;
    private String rootCause;
    private List<Finding> validatedFindings;
    private List<Hypothesis> hypotheses;
    private List<Action> recommendedActions;
    private List<Evidence> citedEvidence;
    private List<Long> coveredAlertIds; // alert incident (root + victim) => link alert
}
