package com.vdt.aiops.metricscraper;

import java.time.Instant;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Statistics;
import com.vdt.aiops.config.properties.AiopsProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/* Metrics Scraper */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricScraper {

    private final DockerClient dockerClient;
    private final MetricSnapshotRepository metricSnapshotRepository;
    private final RestClient restClient;
    private final AiopsProperties aiopsProperties;

    /* Start scrape for each container */
    @Scheduled(fixedDelayString = "${aiops.monitoring.poll-interval-ms}")
    public void scrape() {
        List<Container> containers = dockerClient.listContainersCmd().exec();
        containers.forEach(
                c -> {
                    String containerId = c.getId();
                    String containerName = c.getNames()[0].substring(1);
                    String containerState = c.getState();
                    Thread.ofVirtual()
                            .name("metric-" + containerName)
                            .start(() -> scrapeContainer(containerId, containerName, containerState));
                });
    }

    /* Docker Stats CPU/RAM */
    private void scrapeContainer(String containerId, String containerName, String containerState) {
        try {
            log.debug("Scrape from docker stats");
            /* Only containers has http metrics */
            List<String> httpMetricsContainers = aiopsProperties.getMonitoring().getHttpMetricsContainers();
            dockerClient.statsCmd(containerId)
                    .withNoStream(true) /* Because schedule to scrape, so no need to stream realtime */
                    .exec(new ResultCallback.Adapter<Statistics>() {
                        @Override
                        public void onNext(Statistics s) {
                            Long cpuDelta = s.getCpuStats().getCpuUsage().getTotalUsage() /* cpu for container */
                                    - s.getPreCpuStats().getCpuUsage().getTotalUsage();
                            Long systemDelta = s.getCpuStats().getSystemCpuUsage() /* cpu for whole system */
                                    - s.getPreCpuStats().getSystemCpuUsage();
                            Long cores = s.getCpuStats().getOnlineCpus(); /* running cores */
                            Double cpuPercent = systemDelta > 0 ? 1.0 * cpuDelta / systemDelta * cores * 100.0 : 0.0;

                            Long memUsed = s.getMemoryStats().getUsage();
                            Long memLimit = s.getMemoryStats().getLimit();
                            Double memPercent = memLimit > 0 ? 1.0 * memUsed / memLimit * 100.0 : 0.0;

                            Double requestRate = null;
                            Double errorRate = null;
                            Double latencyMs = null;
                            if (httpMetricsContainers.contains(containerName)) {
                                requestRate = queryPrometheus("rate(http_requests_total[1m])");
                                errorRate = queryPrometheus("rate(http_errors_total[1m])");
                                latencyMs = queryPrometheus("http_latency_avg_ms");
                            }

                            /* is Container healthy? */
                            boolean isHealthy = "running".equals(containerState);

                            MetricSnapshot entry = MetricSnapshot.builder()
                                    .container(containerName)
                                    .scrapedAt(Instant.now())
                                    .cpuPercent(cpuPercent)
                                    .memPercent(memPercent)
                                    .memUsedBytes(memUsed)
                                    .requestRate(requestRate)
                                    .errorRate(errorRate)
                                    .latencyMs(latencyMs)
                                    .healthy(isHealthy)
                                    .build();
                            metricSnapshotRepository.save(entry);
                        }
                    })
                    .awaitCompletion();
        } catch (InterruptedException e) {
            log.warn("Thread Interrupted");
            Thread.currentThread().interrupt();
        }
    }

    /* get stats request, errors, http_lantency_avg_ms */
    private double queryPrometheus(String promql) {
        try {
            log.debug("Scrape from Prometheus");
            PrometheusResponse response = restClient.get()
                    .uri(aiopsProperties.getMonitoring().getPrometheusUrl() + "/api/v1/query?query={promql}", promql)
                    .retrieve() /* send http request and prepare to read response */
                    .body(PrometheusResponse.class); /* jackson parse to Prometheus object */
            if (response == null || response.getData() == null || response.getData().getResult() == null
                    || response.getData().getResult().isEmpty()) {
                return 0.0;
            }
            String raw = response.getData().getResult().get(0).getValue().get(1).toString();
            return Double.parseDouble(raw);
        } catch (Exception e) {
            log.warn("Prometheus query failed: {}", promql);
            return 0.0;
        }
    }
}
