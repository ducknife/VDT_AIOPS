package com.vdt.aiops.monitoring.metricscraper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Statistics;
import com.vdt.aiops.utils.MonitoredServices;
import com.vdt.aiops.utils.ServiceName;

import lombok.RequiredArgsConstructor;

/* Scrape Metrics Instantly, use serialization */
@Service
@RequiredArgsConstructor
public class MetricCollector {

    private final DockerClient dockerClient;
    private final PrometheusClient prometheusClient;
    private final MetricProfiles metricProfiles;
    private final MonitoredServices monitoredServices;

    /* Get Metrics of Service */
    public MetricsOfService collect(String service) {

        /* Spec metrics */
        Map<String, Double> probes = new LinkedHashMap<>();
        for (MetricProbe p : metricProfiles.forService(service)) {
            probes.put(p.getLabel(), prometheusClient.query(p.getPromQL()));
        }

        Container container = monitoredServices.list().stream()
                .filter(c -> service.equals(ServiceName.serviceName(c)))
                .findFirst()
                .orElse(null);

        /* Docker stats */
        double cpuPercent = 0.0, memPercent = 0.0, memUsedMb = 0.0;
        String state = "down";
        if (container != null) {
            state = container.getState();
            Statistics s = fetchStats(container.getId());
            if (s != null) {
                long memUsed = s.getMemoryStats().getUsage() != null ? s.getMemoryStats().getUsage() : 0;
                long memLimit = s.getMemoryStats().getLimit() != null ? s.getMemoryStats().getLimit() : 0;
                memPercent = memLimit > 0 ? 100.0 * memUsed / memLimit : 0.0;
                memUsedMb = memUsed / 1024.0 / 1024.0;
                cpuPercent = cpuPercent(s);
            }
        }

        return MetricsOfService.builder()
                .service(service)
                .state(state)
                .cpuPercent(cpuPercent)
                .memPercent(memPercent)
                .memUsedMb(memUsedMb) /* MB */
                .probes(probes)
                .build();
    }

    /* fetch stats */
    private Statistics fetchStats(String containerId) {
        try {
            List<Statistics> stats = new ArrayList<>();
            dockerClient.statsCmd(containerId)
                    .withNoStream(true)
                    .exec(new ResultCallback.Adapter<Statistics>() {
                        @Override
                        public void onNext(Statistics s) {
                            stats.add(s);
                        }
                    })
                    .awaitCompletion();
            return stats.isEmpty() ? null : stats.get(0);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /* calc cpu % */
    private double cpuPercent(Statistics s) {
        try {
            long cpuDelta = s.getCpuStats().getCpuUsage().getTotalUsage()
                    - s.getPreCpuStats().getCpuUsage().getTotalUsage();
            long systemDelta = s.getCpuStats().getSystemCpuUsage()
                    - s.getPreCpuStats().getSystemCpuUsage();
            Long cores = s.getCpuStats().getOnlineCpus();
            if (systemDelta <= 0 || cores == null)
                return 0.0;
            return 100.0 * cpuDelta / systemDelta * cores;
        } catch (Exception e) {
            return 0.0;
        }
    }
}
