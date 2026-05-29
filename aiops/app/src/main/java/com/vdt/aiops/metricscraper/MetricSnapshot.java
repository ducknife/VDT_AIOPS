package com.vdt.aiops.metricscraper;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "metric_snapshots")
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetricSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String container;
    private Instant scrapedAt;
    private Double cpuPercent;
    private Double memPercent;
    private Long memUsedBytes;
    private Double requestRate;
    private Double errorRate;
    private Double latencyMs;
    private Boolean healthy;
}
