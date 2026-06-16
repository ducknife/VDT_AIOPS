package com.vdt.aiops.config.websocket.snapshot;

import java.util.List;

import com.vdt.aiops.agent.incident.Incident;
import com.vdt.aiops.monitoring.alertmanager.Alert;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/* Snapshot Card for snapshot  */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SnapshotCard {
    private String investigationId;
    private List<Incident> incidents;
    private List<Alert> alerts;
}
