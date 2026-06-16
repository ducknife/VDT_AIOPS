package com.vdt.aiops.tools.read;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import com.vdt.aiops.monitoring.logcollector.Log;
import com.vdt.aiops.monitoring.logcollector.LogRepository;
import com.vdt.aiops.monitoring.logcollector.enums.LogLevel;

import lombok.RequiredArgsConstructor;

/* Fetch logs from database */
@Component
@RequiredArgsConstructor
public class GetServiceLogs {

    private final LogRepository logRepository;

    @Tool(description = "Fetch logs of a specific service between two timestamps. " +
            "Use this when investigating errors or anomalies in a service. " +
            "Parameters: service name (e.g. 'nginx', 'node-api', 'redis', 'postgres'), " +
            "fromIso and toIso in ISO-8601 format (e.g. '2024-01-01T10:00:00Z'). " +
            "Optional logLevel: ERROR, WARN, or INFO. To include ALL levels, " +
            "OMIT this parameter entirely (do not pass the string \"null\").")
    public String fetchLogs(String service, String fromIso, String toIso, String logLevel) {
        try {
            Instant from = Instant.parse(fromIso);
            Instant to = Instant.parse(toIso);
            LogLevel level = null;
            // treat "null"/blank as "no filter" — LLMs often pass the literal string "null"
            if (logLevel != null && !logLevel.isBlank() && !logLevel.equalsIgnoreCase("null")) {
                try {
                    level = LogLevel.valueOf(logLevel.trim().toUpperCase());
                } catch (IllegalArgumentException e) {
                    return "Invalid logLevel. Use INFO, WARN, or ERROR.";
                }
            }
            List<Log> logs = level == null
                    ? logRepository.findTop100ByServiceAndLoggedAtBetweenOrderByLoggedAtDesc(service, from, to)
                    : logRepository.findTop100ByServiceAndLogLevelAndLoggedAtBetweenOrderByLoggedAtDesc(service, level,
                            from, to);
            if (logs.isEmpty()) {
                return "No logs found for container: " + service;
            }
            // repo returns newest-first; reverse to chronological for readability
            Collections.reverse(logs);
            String header = logs.size() == 100
                    ? "Service: " + service + " | showing latest 100 logs (more exist - narrow the time window)\n"
                    : "Service: " + service + " | Logs: " + logs.size() + "\n";
            return header
                    + logs.stream()
                            .map(this::format)
                            .collect(Collectors.joining("\n"));

        } catch (DateTimeParseException e) {
            return "Invalid timestamp format. Use ISO-8601, e.g. 2024-01-01T10:00:00Z";
        }
    }

    private String format(Log l) {
        return "[" + l.getLoggedAt() + "] " + l.getLogLevel() + ": " + l.getMessage();
    }
}
