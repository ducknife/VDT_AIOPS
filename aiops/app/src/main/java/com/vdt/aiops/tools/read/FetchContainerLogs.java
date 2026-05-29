package com.vdt.aiops.tools.read;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import com.vdt.aiops.logcollector.Log;
import com.vdt.aiops.logcollector.LogRepository;

import lombok.RequiredArgsConstructor;

/* Fetch logs from database */
@Component
@RequiredArgsConstructor
public class FetchContainerLogs {

    private final LogRepository logRepository;

    @Tool(description = "fetch logs of a container in a time range")
    public String fetchLogs(String container, String fromIso, String toIso) {
        Instant from = Instant.parse(fromIso);
        Instant to = Instant.parse(toIso);
        List<Log> logs = logRepository.findByContainerAndLoggedAtBetween(container, from, to);
        if (logs.isEmpty()) {
            return "No logs found for container: " + container;
        }
        return logs.stream()
                .map(l -> "[" + l.getLoggedAt() + "]" + " " + l.getLogLevel()
                        + " " + l.getContainer() + ": " + l.getMessage())
                .collect(Collectors.joining("\n"));
    }
}
