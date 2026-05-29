package com.vdt.aiops.logcollector;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;

public interface LogRepository extends JpaRepository<Log, Long> {

    List<Log> findByContainerAndLoggedAtBetween(String container, Instant from, Instant to);
}
