package com.vdt.aiops.utils;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import com.vdt.aiops.monitoring.alertmanager.Alert;

public class AlertUtils {
    public static Instant earliest(List<Alert> alerts) {
        return alerts.stream()
                .map(Alert::getDetectedAt)
                .min(Comparator.naturalOrder())
                .orElse(Instant.now());
    }

    public static Instant latest(List<Alert> alerts) {
        return alerts.stream()
                .map(Alert::getDetectedAt)
                .max(Comparator.naturalOrder())
                .orElse(Instant.now());
    }
}
