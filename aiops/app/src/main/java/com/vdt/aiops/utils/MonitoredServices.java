package com.vdt.aiops.utils;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MonitoredServices {
    private final DockerClient dockerClient;
    private final String MONITORED_SYSTEM = "aiops-sim";

    public List<Container> list() {
        return dockerClient.listContainersCmd().exec().stream()
                .filter(c -> MONITORED_SYSTEM.equals(c.getLabels().get("com.docker.compose.project")))
                .collect(Collectors.toList());
    }
}
