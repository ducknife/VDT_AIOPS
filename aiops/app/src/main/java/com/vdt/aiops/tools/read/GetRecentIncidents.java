package com.vdt.aiops.tools.read;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import com.vdt.aiops.agent.incident.Incident;
import com.vdt.aiops.agent.incident.IncidentRepository;

import lombok.RequiredArgsConstructor;

/* The N most recent incidents (any status) - so chat can answer "show the last N incidents". */
@Component
@RequiredArgsConstructor
public class GetRecentIncidents {

    private final IncidentRepository incidentRepository;

    @Tool(description = """
            List the N most recent incidents across ALL services and ALL statuses (including \
            RESOLVED), newest first. Use this when the operator asks for a specific number of \
            recent incidents (e.g. 'show the last 10 incidents', 'recent 3 incidents') or wants \
            history / recurrence context beyond just the currently-open ones. For ONLY the open \
            incidents use listActiveIncidents instead. \
            Parameter 'length': how many incidents to return (a positive integer, e.g. 10). \
            Returns, per incident: id, analyzed time, severity, status, service, title, and root cause.
            """)
    public String getRecentIncident(Long length) {
        if (length == null || length < 1)
            return "Length must be a positive integer (e.g. 10).";

        List<Incident> recent = incidentRepository.findRecentIncidents(length);
        if (recent.isEmpty())
            return "No incidents recorded yet.";

        return recent.size() + " most recent incident(s):\n"
                + recent.stream().map(this::format).collect(Collectors.joining("\n"));
    }

    private String format(Incident i) {
        return "- #" + i.getId() + " [" + i.getAnalyzedAt() + "] " + i.getSeverity()
                + " | " + i.getStatus() + " | " + i.getService()
                + " | " + i.getTitle() + " | root: " + i.getRootCause();
    }
}
