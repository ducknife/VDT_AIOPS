package com.vdt.aiops.monitoring.logcollector;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vdt.aiops.monitoring.logcollector.enums.LogLevel;

import java.time.Instant;
import java.util.List;

public interface LogRepository extends JpaRepository<Log, Long> {

    List<Log> findByContainerAndLoggedAtBetween(String container, Instant from, Instant to);

    List<Log> findByContainerAndLogLevelAndLoggedAtBetween(String container, LogLevel logLevel, Instant from, Instant to);
}
