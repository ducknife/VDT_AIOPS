package com.vdt.aiops.agent.incident;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;



public interface IncidentRepository extends JpaRepository<Incident, Long> {

    /* Recent incidents of a service, newest first - for recurrence detection */
    List<Incident> findTop5ByServiceOrderByAnalyzedAtDesc(String service);

    /* for snapshot, problem: when TUI not connected, incidents could be created, so
    when TUi open, show incidents created */
    List<Incident> findTop10ByOrderByAnalyzedAtDesc();
}
