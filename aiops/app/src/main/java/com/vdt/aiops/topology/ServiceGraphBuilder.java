package com.vdt.aiops.topology;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.github.dockerjava.api.model.Container;
import com.vdt.aiops.topology.enums.ServiceRole;
import com.vdt.aiops.utils.MonitoredContainers;
import com.vdt.aiops.utils.ServiceName;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/* Start build graph from Docker */

@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceGraphBuilder {

    private final MonitoredContainers monitoredContainers;
    private volatile ServiceGraph cached; /* volatile because it is common resource */

    @PostConstruct
    public void init() {
        refresh();
    }

    /* 1 thread/time can call this */
    public synchronized void refresh() {
        this.cached = build();
        log.info("ServiceGraph: {} services", cached.allNodes().size());
    }

    public ServiceGraph getGraph() {
        return cached;
    }

    /* Build graph */
    public ServiceGraph build() {
        Map<String, ServiceNode> nodes = new HashMap<>();
        Map<String, Set<String>> downstream = new HashMap<>();
        for (Container c : monitoredContainers.list()) {
            String name = ServiceName.serviceName(c);
            String image = c.getImage();
            ServiceRole role = classifyRole(name, image);
            nodes.put(name, new ServiceNode(name, c.getId(), role));
            downstream.put(name, dependencies(c));
        }
        return new ServiceGraph(nodes, downstream);
    }

    /* Build egde of graph based on depends_on label */
    private Set<String> dependencies(Container c) {
        String raw = c.getLabels().get("com.docker.compose.depends_on");
        if (raw == null || raw.isBlank())
            return Set.of();
        Set<String> deps = new HashSet<>();
        for (String item : raw.split("[,]+")) {
            String dep = item.split("[:]+")[0].trim();
            if (!dep.isBlank())
                deps.add(dep);
        }
        return deps;
    }

    /*
     * Classify role for service: Note that: image is tech that runned this service,
     * name is container name, label is service name
     */
    private ServiceRole classifyRole(String name, String image) {
        String haystack = (name + " " + image).toLowerCase();
        if (haystack.contains("exporter"))
            return ServiceRole.EXPORTER;
        if (containsAny(haystack, "postgre", "redis"))
            return ServiceRole.DATASTORE;
        if (containsAny(haystack, "nginx"))
            return ServiceRole.PROXY;
        if (containsAny(haystack, "traffic"))
            return ServiceRole.LOADGEN;
        return ServiceRole.APP;
    }

    /* Check if haystack contains any key */
    private boolean containsAny(String haystack, String... keywords) {
        for (String kw : keywords) {
            if (haystack.contains(kw))
                return true;
        }
        return false;
    }

}
