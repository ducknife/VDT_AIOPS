package com.vdt.aiops.tools.read;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import com.vdt.aiops.agent.incident.Incident;
import com.vdt.aiops.agent.incident.IncidentRepository;

import lombok.RequiredArgsConstructor;

/* Past incidents of a service - recurrence detection */
@Component
@RequiredArgsConstructor
public class GetIncidentHistory {

    private final IncidentRepository incidentRepository;

    @Tool(description = """
            Return the most recent past incidents diagnosed for a service (up to 5, newest first). \
            Use this to detect RECURRING problems: if this service had a similar incident before, \
            the current root cause may be the same as last time. \
            Returns, per past incident: when it was analyzed, severity, title, and root cause. \
            Parameter 'service': the service name, e.g. 'node-api', 'redis', 'postgres'.
            """)
    public String getHistory(String service) {
        List<Incident> incidents = incidentRepository.findTop5ByServiceOrderByAnalyzedAtDesc(service);
        if (incidents.isEmpty()) {
            return "No past incidents for service: " + service;
        }
        return "Past incidents for " + service + ": " + incidents.size() + "\n"
                + incidents.stream().map(this::format).collect(Collectors.joining("\n"));
    }

    private String format(Incident i) {
        return "- [" + i.getAnalyzedAt() + "] " + i.getSeverity() + " | " + i.getTitle()
                + " | root: " + i.getRootCause();
    }
}
