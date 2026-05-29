package com.vdt.aiops.metricscraper;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MetricSnapshotRepository extends JpaRepository<MetricSnapshot, Long>{
    /* find top 20 by container and scraped time */
    List<MetricSnapshot> findTop20ByContainerOrderByScrapedAtDesc(String container);
    /* find by container in a time range */
    List<MetricSnapshot> findByContainerAndScrapedAtBetween(String container, Instant from, Instant to);
} 