package com.vdt.aiops.agent.context;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.vdt.aiops.monitoring.alertmanager.Alert;
import com.vdt.aiops.monitoring.logcollector.LogGroup;
import com.vdt.aiops.monitoring.metricscraper.MetricsOfService;
import com.vdt.aiops.topology.enums.ServiceRole;

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
public class ContextBundle {
    private String focus; 
    private List<Alert> alerts;
    private Instant windowFrom, windowTo; // +- 5 minutes
    private ServiceRole focusRole; // role of focus service
    private Map<String, ServiceRole> downstream;
    private Map<String, ServiceRole> upstream;
    // Scope: metrics of focus + it's downstream
    private Map<String, MetricsOfService> metrics;
    private Map<String, List<LogGroup>> logs; // key: service's name, value: LogGroup of that service
}
