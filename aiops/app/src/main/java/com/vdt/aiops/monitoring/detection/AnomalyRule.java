package com.vdt.aiops.monitoring.detection;

import com.vdt.aiops.monitoring.detection.enums.AnomalyType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AnomalyRule {
    private String serviceType;
    private String signal;
    private boolean greaterThan;
    private double threshold;
    private AnomalyType type;

    /* compare value */
    public boolean breached(double value) {
        if (greaterThan) {
            return value > threshold;
        }
        return value < threshold;
    }
}
