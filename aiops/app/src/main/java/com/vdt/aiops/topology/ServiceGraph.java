package com.vdt.aiops.topology;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.vdt.aiops.topology.enums.ServiceRole;

/* Graph of services */
public class ServiceGraph {
    private final Map<String, ServiceNode> nodes; /* nodes of graph */
    private final Map<String, Set<String>> downstream; /* dependencies — services that A depends on → root-cause candidates */
    private final Map<String, Set<String>> upstream;   /* dependents — services that depend on A → blast radius / impact */
    /* exp: a -> b, a -> c ==> downstream(a) = {b, c}, upstream(b) = {a}, upstream(c) = {a} */
    public ServiceGraph(Map<String, ServiceNode> nodes, Map<String, Set<String>> downstream) {
        this.nodes = nodes;
        this.downstream = downstream;
        this.upstream = invert(downstream);
    }

    /* invert downstream to upstream */
    private static Map<String, Set<String>> invert(Map<String, Set<String>> ds) {
        Map<String, Set<String>> us = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : ds.entrySet()) {
            String currentService = entry.getKey();
            for (String dependency : entry.getValue()) {
                if (!us.containsKey(dependency)) {
                    us.put(dependency, new HashSet<>());
                }
                us.get(dependency).add(currentService);
            }
        }
        return us;
    }

    /* Get downstream of a service */
    public Set<String> downstream(String service) {
        return downstream.getOrDefault(service, Set.of());
    }

    /* Get upstream of a service */
    public Set<String> upstream(String service) {
        return upstream.getOrDefault(service, Set.of());
    }

    /* Get node of service */
    public ServiceNode node(String service) {
        return nodes.get(service);
    }

    /* Get role of service */
    public ServiceRole role(String service) {
        ServiceNode node = nodes.get(service);
        return node != null ? node.getRole() : ServiceRole.UNKNOWN;
    }

    /* Get All Nodes */
    public Collection<ServiceNode> allNodes() {
        return nodes.values();
    }
}
