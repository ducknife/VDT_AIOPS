package com.vdt.aiops.agent.incident;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Human feedback on one incident's diagnosis. 1:1 with the incident (incident_id is the PK).
 * Store-only — kept for later evaluation, NOT fed back to the agent as context.
 */
@Entity
@Table(name = "incident_feedback")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentFeedback {

    @Id
    @Column(name = "incident_id")
    private Long incidentId; // PK, not generated -> save() upserts (one feedback per incident)

    private String verdict; // correct | partial | wrong

    // miss-taxonomy: what the diagnosis got wrong / left out (set when verdict != correct)
    // wrong-root-cause | missed-service | wrong-severity | missed-split | wrong-remediation | other
    private String missed;

    private String note;

    @Builder.Default
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
}
