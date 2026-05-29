package com.vdt.aiops.logcollector;

import java.time.Instant;

import org.springframework.stereotype.Service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/* Log collecter use Docker API */
/* Run only one time */
@Slf4j
@Service
@RequiredArgsConstructor
public class DockerLogCollector {

    private final DockerClient dockerClient;
    private final LogRepository logRepository;

    /* start all stream for all containers */
    public void startAll() {
        log.info("=== startAll() called ===");
        var containers = dockerClient.listContainersCmd().exec();
        log.info("Found {} containers", containers.size());
        containers.forEach(c -> {
            String containerId = c.getId();
            String containerName = c.getNames()[0].substring(1);
            log.info("Spawning thread for: {}", containerName);
            Thread.ofVirtual()
                    .name("log-" + containerName)
                    .start(() -> streamContainer(containerId, containerName));
        });
    }

    /* Stream logs by container ID */
    private void streamContainer(String containerId, String containerName) {
        try {
            log.info("Starting log stream: {}", containerName);
            dockerClient.logContainerCmd(containerId)
                    .withFollowStream(true)
                    .withStdOut(true) /* get normal logs */
                    .withStdErr(true) /* get error logs */
                    .withTail(100)
                    .exec(new ResultCallback.Adapter<Frame>() {
                        @Override
                        public void onNext(Frame frame) {
                            try {
                                String line = new String(frame.getPayload()).trim();
                                if (line.isEmpty()) return;
                                String logLevel = parseLevel(line);
                                Log entry = Log.builder()
                                        .container(containerName)
                                        .logLevel(logLevel)
                                        .message(line)
                                        .loggedAt(Instant.now())
                                        .build();
                                logRepository.save(entry);
                            } catch (Exception e) {
                                log.error("Failed to save log from {}: {}", containerName, e.getMessage());
                            }
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            log.error("Log stream error for {}: {}", containerName, throwable.getMessage());
                        }
                    })
                    .awaitCompletion();
        } catch (InterruptedException e) {
            log.warn("Thread interrupted");
            Thread.currentThread().interrupt();
        }
    }

    /* parse log */
    private String parseLevel(String line) {
        String upper = line.toUpperCase();
        if (upper.contains("ERROR"))
            return "ERROR";
        if (upper.contains("WARN") || upper.contains("WARNING"))
            return "WARN";
        if (upper.matches(".*\" [45]\\d\\d .*"))
            return "ERROR"; /* nginx 4xx/5xx error */
        return "INFO";
    }
}
