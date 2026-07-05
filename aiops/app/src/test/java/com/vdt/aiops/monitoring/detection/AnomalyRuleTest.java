package com.vdt.aiops.monitoring.detection;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.vdt.aiops.monitoring.detection.enums.AnomalyType;

/**
 * Unit tests for the static-threshold rule. Emphasis on the FALSE-POSITIVE side:
 * a healthy metric (below a "greater-than" limit, or above a "less-than" limit,
 * including exactly at the boundary) must NOT breach, so no spurious alert fires.
 */
class AnomalyRuleTest {

    // e.g. HIGH_LATENCY: P99 latency (ms) > 2000 is abnormal
    private AnomalyRule greaterThan(double threshold) {
        return AnomalyRule.builder()
                .serviceType("node-api").signal("p99_ms")
                .greaterThan(true).threshold(threshold)
                .type(AnomalyType.HIGH_LATENCY).build();
    }

    // e.g. SERVICE_DOWN: up < 1 (up == 0) is abnormal
    private AnomalyRule lessThan(double threshold) {
        return AnomalyRule.builder()
                .serviceType("postgres").signal("up")
                .greaterThan(false).threshold(threshold)
                .type(AnomalyType.SERVICE_DOWN).build();
    }

    @Test
    void greaterThan_healthyValueBelowLimit_doesNotBreach() {
        assertThat(greaterThan(2000).breached(1500)).isFalse();
    }

    @Test
    void greaterThan_valueAboveLimit_breaches() {
        assertThat(greaterThan(2000).breached(2500)).isTrue();
    }

    @Test
    void greaterThan_valueExactlyAtLimit_doesNotBreach() {
        // strict '>' — a metric sitting exactly on the line is NOT an alert (no false positive)
        assertThat(greaterThan(2000).breached(2000)).isFalse();
    }

    @Test
    void lessThan_healthyValueAboveLimit_doesNotBreach() {
        // up == 1 (service alive) is healthy against an 'up < 1' rule
        assertThat(lessThan(1).breached(1)).isFalse();
    }

    @Test
    void lessThan_valueBelowLimit_breaches() {
        // up == 0 (service down)
        assertThat(lessThan(1).breached(0)).isTrue();
    }

    @Test
    void healthyBaseline_noValueBreaches() {
        // a stream of normal P99 readings around ~1.2-1.9s never trips a 2s limit -> zero false alerts
        AnomalyRule rule = greaterThan(2000);
        double[] healthy = {1200, 1450, 1300, 1890, 1750, 1600, 1999.9};
        for (double v : healthy) {
            assertThat(rule.breached(v))
                    .as("healthy reading %.1f must not breach", v)
                    .isFalse();
        }
    }
}
