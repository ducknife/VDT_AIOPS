package com.vdt.aiops.tools.read;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse.ContainerState;
import com.github.dockerjava.api.model.Container;
import com.vdt.aiops.utils.MonitoredServices;
import com.vdt.aiops.utils.ServiceName;

import lombok.RequiredArgsConstructor;

/* Low-level Docker state of a service's container (restart count, health, image) */
@Component
@RequiredArgsConstructor
public class InspectContainer {

    private final DockerClient dockerClient;
    private final MonitoredServices monitoredServices;

    @Tool(description = """
            Inspect the low-level Docker state of a service's container: running state, \
            RESTART COUNT (a high or rising count means the container is crash-looping), \
            health-check status, image, and created time. Use this to check whether a service \
            is repeatedly restarting or was recently (re)created. \
            Parameter 'service': the service name, e.g. 'node-api', 'redis', 'postgres', 'nginx'.
            """)
    public String inspect(String service) {
        Container container = monitoredServices.list().stream()
                .filter(c -> service.equals(ServiceName.serviceName(c)))
                .findFirst()
                .orElse(null);
        if (container == null) {
            return "No container found for service: " + service;
        }

        InspectContainerResponse r = dockerClient.inspectContainerCmd(container.getId()).exec();
        ContainerState state = r.getState();

        Integer restarts = r.getRestartCount();
        String health = (state.getHealth() == null) ? "n/a" : state.getHealth().getStatus();
        String image = (r.getConfig() == null) ? "n/a" : r.getConfig().getImage();

        return "Service: " + service + "\n"
                + "state=" + state.getStatus() + " (running=" + state.getRunning() + ")\n"
                + "restartCount=" + (restarts == null ? 0 : restarts) + "\n"
                + "health=" + health + "\n"
                + "image=" + image + "\n"
                + "created=" + r.getCreated();
    }
}
