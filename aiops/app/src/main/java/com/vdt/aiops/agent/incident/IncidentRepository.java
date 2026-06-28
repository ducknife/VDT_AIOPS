package com.vdt.aiops.agent.incident;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.vdt.aiops.agent.incident.enums.IncidentStatus;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    /* Recent incidents of a service, newest first - for recurrence detection */
    List<Incident> findTop5ByServiceOrderByAnalyzedAtDesc(String service);

    /*
     * for snapshot, problem: when TUI not connected, incidents could be created, so
     * when TUi open, show incidents created
     */
    List<Incident> findTop5ByOrderByAnalyzedAtDesc();

    List<Incident> findTop5ByStatusNotOrderByAnalyzedAtDesc(IncidentStatus status);

    // native SQL: 'SELECT *', 'LIMIT', tên bảng/cột là cú pháp SQL thật -> phải nativeQuery=true
    @Query(value = "SELECT * FROM incidents ORDER BY analyzed_at DESC LIMIT :length", nativeQuery = true)
    List<Incident> findRecentIncidents(@Param("length") Long length);
}
