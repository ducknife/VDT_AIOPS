package com.vdt.aiops.monitoring.logcollector;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
import com.vdt.aiops.monitoring.logcollector.enums.LogLevel;
import com.vdt.aiops.utils.MonitoredContainers;
import com.vdt.aiops.utils.ServiceName;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/* Log collecter use Docker API */
/* Run only one time */
@Slf4j
@Service
@RequiredArgsConstructor
public class DockerLogCollector {

    private final DockerClient dockerClient;
    private final MonitoredContainers monitoredContainers;
    private final LogRepository logRepository;
    /* must use ConcurrentLinkedQueue because it's thread-safe */
    private final ConcurrentLinkedQueue<Log> buffer = new ConcurrentLinkedQueue<>();

    /* start all stream for all containers */
    @PostConstruct
    public void startAll() {
        monitoredContainers.list().stream()
                .forEach(c -> {
                    String containerId = c.getId();
                    String containerName = ServiceName.serviceName(c);
                    Thread.ofVirtual()
                            .name("log-" + containerName)
                            .start(() -> streamContainer(containerId, containerName));
                });
    }

    /* flush buffer to db */
    @Scheduled(fixedDelayString = "${aiops.log.flush-interval-ms}")
    @Transactional
    public void flushBuffer() {
        List<Log> batch = new ArrayList<>();
        Log entry;
        while (!buffer.isEmpty()) {
            entry = buffer.poll();
            batch.add(entry);
            if (batch.size() >= 500) {
                logRepository.saveAll(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            logRepository.saveAll(batch); /* save remaining logs if batch size < 500 */
        }
    }

    /* Stream logs by container id and name */
    private void streamContainer(String containerId, String containerName) {
        try {
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
                            LogLevel logLevel = parseLevel(line);
                            Log entry = Log.builder()
                                    .container(containerName)
                                    .logLevel(logLevel)
                                    .message(line)
                                    .loggedAt(Instant.now())
                                    .build();
                            buffer.offer(entry);
                        }
                    })
                    .awaitCompletion();
        } catch (InterruptedException e) {
            log.warn("Thread interrupted");
            Thread.currentThread().interrupt();
        }
    }

    /* parse log */
    private LogLevel parseLevel(String line) {
        String upper = line.toUpperCase();
        if (upper.contains("ERROR"))
            return LogLevel.ERROR;
        if (upper.contains("WARN") || upper.contains("WARNING"))
            return LogLevel.WARN;
        if (upper.matches(".*\" [45]\\d\\d .*"))
            return LogLevel.ERROR; /* nginx 4xx/5xx error */
        return LogLevel.INFO;
    }
}
