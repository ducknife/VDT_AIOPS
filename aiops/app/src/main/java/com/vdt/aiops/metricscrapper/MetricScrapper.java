package com.vdt.aiops.metricscrapper;

import org.jvnet.hk2.annotations.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.github.dockerjava.api.DockerClient;
import com.vdt.aiops.config.properties.AiopsProperties;

import groovy.util.logging.Slf4j;
import lombok.RequiredArgsConstructor;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricScrapper {

    private final DockerClient dockerClient;
    private final MetricSnapshotRepository metricSnapshotRepository;
    private final RestClient restClient;

    @Scheduled(fixedDelayString = "")
    public void scrape() {
        
    }

}
