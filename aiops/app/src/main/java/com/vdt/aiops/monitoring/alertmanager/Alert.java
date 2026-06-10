package com.vdt.aiops.monitoring.alertmanager;

import java.time.Instant;

import com.vdt.aiops.monitoring.alertmanager.enums.AlertStatus;
import com.vdt.aiops.monitoring.detection.enums.AnomalyType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/* Alert entity */
@Table(name = "alerts")
@Entity
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Alert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String service;
    @Enumerated(EnumType.STRING)
    private AnomalyType type;
    private String message;

    @Column(name = "status")
    @Builder.Default
    @Enumerated(EnumType.STRING)
    private AlertStatus status = AlertStatus.ACTIVE;

    @Builder.Default
    private Instant detectedAt = Instant.now();
    private Instant resolvedAt;

    @Column(name = "is_active")
    @Builder.Default
    private boolean active = true;

    private Long incidentId;

    @Builder.Default
    @Column(name = "investigation_attempts", nullable = false)
    private int investigationAttempts = 0;
}
