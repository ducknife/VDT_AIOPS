package com.vdt.aiops.tools.read;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import com.vdt.aiops.monitoring.alertmanager.Alert;
import com.vdt.aiops.monitoring.alertmanager.AlertRepository;

import lombok.RequiredArgsConstructor;

/* List currently active alerts across the whole system */
@Component
@RequiredArgsConstructor
public class GetActiveAlerts {

    private final AlertRepository alertRepository;

    @Tool(description = """
            List all currently ACTIVE (firing, not yet resolved) alerts across the whole system. \
            Use this to see whether the service you are investigating is part of a larger storm, \
            or to find other anomalies happening at the same time that may share a root cause. \
            Returns, per alert: service, type, status (ACTIVE / INVESTIGATING / DIAGNOSED), message, \
            and when it was detected.
            """)
    public String getActiveAlerts() {
        List<Alert> alerts = alertRepository.findByActiveTrue();
        if (alerts.isEmpty()) {
            return "No active alerts.";
        }
        return "Active alerts: " + alerts.size() + "\n"
                + alerts.stream().map(this::format).collect(Collectors.joining("\n"));
    }

    private String format(Alert a) {
        return "- " + a.getService() + " | " + a.getType() + " | " + a.getStatus()
                + " | " + a.getMessage() + " | detected " + a.getDetectedAt();
    }
}
