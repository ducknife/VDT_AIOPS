package com.vdt.aiops.agent.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/* Published after a human submits feedback on an incident -> broadcast so every TUI updates the badge. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentFeedbackEvent {
    private Long incidentId;
    private String verdict;
}
