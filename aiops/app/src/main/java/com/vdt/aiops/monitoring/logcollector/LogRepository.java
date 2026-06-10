package com.vdt.aiops.monitoring.logcollector;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vdt.aiops.monitoring.logcollector.enums.LogLevel;

import java.time.Instant;
import java.util.List;

public interface LogRepository extends JpaRepository<Log, Long> {

    List<Log> findByServiceAndLoggedAtBetween(String service, Instant from, Instant to);

    List<Log> findByServiceAndLogLevelAndLoggedAtBetween(String service, LogLevel logLevel, Instant from, Instant to);

    List<Log> findTop100ByServiceAndLoggedAtBetweenOrderByLoggedAtDesc(
            String service, Instant from, Instant to);

    List<Log> findTop100ByServiceAndLogLevelAndLoggedAtBetweenOrderByLoggedAtDesc(
            String service, LogLevel level, Instant from, Instant to);

}
