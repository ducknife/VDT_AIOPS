package com.vdt.aiops.monitoring.detection;

import com.vdt.aiops.monitoring.detection.enums.AnomalyType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/* Through it to Alert */
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DetectedAnomaly {
    private String service;
    private AnomalyType type;
    private String message;
}
