package com.vdt.aiops.tools.read;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import com.vdt.aiops.agent.incident.Incident;
import com.vdt.aiops.agent.incident.IncidentRepository;
import com.vdt.aiops.agent.incident.enums.IncidentStatus;

import lombok.RequiredArgsConstructor;

/* List the incidents currently open (not resolved) - so chat can answer "what's broken now". */
@Component
@RequiredArgsConstructor
public class ListActiveIncidents {

    private final IncidentRepository incidentRepository;

    @Tool(description = """
            List the incidents currently OPEN (not yet resolved), newest first (up to 5). \
            Use this when the operator asks what is wrong right now, which services have problems, \
            or refers to an incident without giving its id - find the matching one here first, then \
            call getIncident with its id for the full details. \
            Returns, per incident: id, service, severity, status (NEW/ACKNOWLEDGED), and title.
            """)
    public String fetchActiveIncidents() {
        List<Incident> incidents = incidentRepository
                .findTop5ByStatusNotOrderByAnalyzedAtDesc(IncidentStatus.RESOLVED);
        if (incidents.isEmpty()) {
            return "No active incidents right now - all clear.";
        }
        return "Active incidents (" + incidents.size() + "):\n"
                + incidents.stream().map(this::format).collect(Collectors.joining("\n"));
    }

    private String format(Incident i) {
        return "- #" + i.getId() + " | " + i.getSeverity() + " | " + i.getService()
                + " | " + i.getStatus() + " | " + i.getTitle();
    }
}
