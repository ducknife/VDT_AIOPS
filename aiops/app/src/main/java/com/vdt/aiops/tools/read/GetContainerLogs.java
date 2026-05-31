package com.vdt.aiops.tools.read;

import java.time.Instant;
import java.time.format.DateTimeParseException;
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
public class GetContainerLogs {

    private final LogRepository logRepository;

    @Tool(description = "Fetch logs of a specific container between two timestamps. " +
            "Use this when investigating errors or anomalies in a container. " +
            "Parameters: container name (e.g. 'aiops-nginx'), fromIso and toIso in ISO-8601 format (e.g. '2024-01-01T10:00:00Z'). "
            + "Optional logLevel: ERROR, WARN, INFO — null to get all levels.")
    public String fetchLogs(String container, String fromIso, String toIso, String logLevel) {
        try {
            Instant from = Instant.parse(fromIso);
            Instant to = Instant.parse(toIso);
            LogLevel level = null;
            if (logLevel != null) {
                try {
                    level = LogLevel.valueOf(logLevel.trim().toUpperCase());
                } catch (IllegalArgumentException e) {
                    return "Invalid logLevel. Use INFO, WARN, or ERROR.";
                }
            }
            List<Log> logs = level == null ? logRepository.findByContainerAndLoggedAtBetween(container, from, to)
                    : logRepository.findByContainerAndLogLevelAndLoggedAtBetween(container, level, from, to);
            if (logs.isEmpty()) {
                return "No logs found for container: " + container;
            }
            return "Container: " + container + " | Logs: " + logs.size() + "\n"
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
