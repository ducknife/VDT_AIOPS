package com.vdt.aiops.config;

import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.vdt.aiops.tools.read.GetActiveAlerts;
import com.vdt.aiops.tools.read.GetIncidentHistory;
import com.vdt.aiops.tools.read.GetServiceDependencies;
import com.vdt.aiops.tools.read.GetServiceLogs;
import com.vdt.aiops.tools.read.GetServiceMetrics;
import com.vdt.aiops.tools.read.InspectContainer;
import com.vdt.aiops.tools.read.QueryMetrics;

import lombok.RequiredArgsConstructor;

/* Generate schema JSON for each tool: call exactly that tool */
@Configuration
@RequiredArgsConstructor
public class AgentConfig {

    @Bean
    public ToolCallingManager toolCallingManager() {
        return DefaultToolCallingManager.builder().build();
    }

    /* Tool source */
    @Bean
    public ToolCallback[] nosticChatClient(
            GetServiceLogs getServiceLogs,
            GetServiceMetrics getServiceMetrics,
            GetServiceDependencies getServiceDependencies,
            QueryMetrics queryMetrics,
            GetActiveAlerts getActiveAlerts,
            GetIncidentHistory getIncidentHistory,
            InspectContainer inspectContainer) {
        return ToolCallbacks.from(
                getServiceLogs, getServiceMetrics, getServiceDependencies,
                queryMetrics, getActiveAlerts, getIncidentHistory, inspectContainer);
    }
}
