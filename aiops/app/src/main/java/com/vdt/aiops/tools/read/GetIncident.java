package com.vdt.aiops.tools.read;

import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import com.vdt.aiops.agent.incident.Action;
import com.vdt.aiops.agent.incident.Evidence;
import com.vdt.aiops.agent.incident.Finding;
import com.vdt.aiops.agent.incident.Hypothesis;
import com.vdt.aiops.agent.incident.Incident;
import com.vdt.aiops.agent.incident.IncidentRepository;

import lombok.RequiredArgsConstructor;

/* Full details of ONE incident by id - so chat can drill into a specific incident. */
@Component
@RequiredArgsConstructor
public class GetIncident {

    private final IncidentRepository incidentRepository;

    @Tool(description = """
            Get the FULL details of a single incident by its numeric id: root cause, summary, \
            validated findings, hypotheses, recommended actions, and cited evidence. \
            Use this when the operator references a specific incident (e.g. 'incident #40', \
            'tell me more about this one'), or after listActiveIncidents to drill into one. \
            Parameter 'incidentId': the numeric incident id, e.g. 40.
            """)
    public String fetchIncident(Long incidentId) {
        return incidentRepository.findById(incidentId)
                .map(this::format)
                .orElse("No incident found with id: " + incidentId);
    }

    private String format(Incident i) {
        StringBuilder sb = new StringBuilder();
        sb.append("Incident #").append(i.getId())
                .append(" | ").append(i.getSeverity())
                .append(" | ").append(i.getService())
                .append(" | ").append(i.getStatus())
                .append(" | analyzed ").append(i.getAnalyzedAt()).append("\n");
        sb.append("Title: ").append(i.getTitle()).append("\n");
        if (i.getSummary() != null)
            sb.append("Summary: ").append(i.getSummary()).append("\n");
        if (i.getRootCause() != null)
            sb.append("Root cause: ").append(i.getRootCause()).append("\n");
        appendFindings(sb, i.getValidatedFindings());
        appendHypotheses(sb, i.getHypotheses());
        appendActions(sb, i.getRecommendedActions());
        appendEvidence(sb, i.getCitedEvidence());
        return sb.toString().trim();
    }

    private void appendFindings(StringBuilder sb, List<Finding> fs) {
        if (fs == null || fs.isEmpty()) return;
        sb.append("Validated findings:\n");
        fs.forEach(f -> sb.append("  - ").append(f.getFinding())
                .append(" (evidence: ").append(f.getEvidence()).append(")\n"));
    }

    private void appendHypotheses(StringBuilder sb, List<Hypothesis> hs) {
        if (hs == null || hs.isEmpty()) return;
        sb.append("Hypotheses (need verification):\n");
        hs.forEach(h -> sb.append("  - ").append(h.getClaim())
                .append(" (verify: ").append(h.getNeedsVerification()).append(")\n"));
    }

    private void appendActions(StringBuilder sb, List<Action> as) {
        if (as == null || as.isEmpty()) return;
        sb.append("Recommended actions:\n");
        as.forEach(a -> sb.append("  - ").append(a.getAction())
                .append(" (why: ").append(a.getWhy()).append(")\n"));
    }

    private void appendEvidence(StringBuilder sb, List<Evidence> es) {
        if (es == null || es.isEmpty()) return;
        sb.append("Cited evidence:\n");
        es.forEach(e -> sb.append("  - ").append(e.getSource())
                .append(": ").append(e.getDetail()).append("\n"));
    }
}
