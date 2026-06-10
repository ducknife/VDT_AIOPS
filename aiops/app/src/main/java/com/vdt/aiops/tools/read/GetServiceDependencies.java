package com.vdt.aiops.tools.read;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import com.vdt.aiops.topology.ServiceGraph;
import com.vdt.aiops.topology.ServiceGraphBuilder;

import lombok.RequiredArgsConstructor;


/* Get related services of a service */
@Component
@RequiredArgsConstructor
public class GetServiceDependencies {

    private final ServiceGraphBuilder serviceGraphBuilder;

    @Tool(description = """
    Returns the dependency relationships of a service in the monitored system. \
    Use this when investigating why a service is anomalous (slow, erroring, down) \
    to find which related services to inspect next.
    - downstream (dependencies): services this service depends on / calls. \
      These are the PRIMARY root-cause suspects — inspect these first.
    - upstream (dependents): services that depend on this one. \
      These are the blast radius — usually symptoms, not the cause.
    - role: one of PROXY, APP, DATASTORE, EXPORTER, LOADGEN, UNKNOWN.
    Parameter 'service': the service name, e.g. 'node-api', 'redis', 'postgres'.
    """)
    public String getDependencies(String service) {
        ServiceGraph graph = serviceGraphBuilder.getGraph();
        return "Service: " + service + " [" + graph.role(service) + "]\n"
            + "-depends on (downstream): " + graph.downstream(service) + "\n"
            + "-depended by (upstream): " + graph.upstream(service);
    }
}
