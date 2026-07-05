package com.vdt.aiops.agent.incident;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentFeedbackRepository extends JpaRepository<IncidentFeedback, Long> {
}
