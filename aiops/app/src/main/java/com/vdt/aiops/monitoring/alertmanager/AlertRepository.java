package com.vdt.aiops.monitoring.alertmanager;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vdt.aiops.monitoring.detection.enums.AnomalyType;

public interface AlertRepository extends JpaRepository<Alert, Long> {
    Optional<Alert> findByServiceAndTypeAndActiveTrue(String service, AnomalyType type);

    List<Alert> findByActiveTrue();
}
