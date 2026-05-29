package com.vdt.aiops.logcollector;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Container;
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
        dockerClient.listContainersCmd().exec()
                .forEach(c -> {
                    String containerName = c.getNames()[0].substring(1);
                    Thread.ofVirtual()
                            .name("log-" + containerName)
                            .start(() -> streamContainer(containerName));
                });
    }

    /* Stream logs by container ID */
    private void streamContainer(String containerName) {
        String containerId = resolveContainerId(containerName);
        try {
            log.debug("Stream logs from container: {}", containerName);
            dockerClient.logContainerCmd(containerId)
                    .withFollowStream(true) /* stream */
                    .withStdOut(true) /* get normal logs */
                    .withStdErr(true) /* get error logs */
                    .withTail(0) /* ignore all logs before, only fetch logs from now */
                    .exec(new ResultCallback.Adapter<Frame>() {
                        @Override
                        public void onNext(Frame frame) {
                            String line = new String(frame.getPayload()).trim();
                            if (line.isEmpty())
                                return;
                            String logLevel = parseLevel(line);
                            Log entry = Log.builder()
                                    .container(containerName)
                                    .logLevel(logLevel)
                                    .message(line)
                                    .loggedAt(Instant.now())
                                    .build();
                            logRepository.save(entry);
                        }
                    })
                    .awaitCompletion();
        } catch (InterruptedException e) {
            log.warn("Thread interrupted");
            Thread.currentThread().interrupt();
        }
    }

    /* Docker API need container ID, not container name */
    private String resolveContainerId(String containerName) {
        List<Container> containers = dockerClient.listContainersCmd().exec();
        return containers.stream()
                .filter(c -> Arrays.stream(c.getNames())
                        .anyMatch(n -> n.equals("/" + containerName)))
                .map(n -> n.getId())
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Container Not Found"));
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
