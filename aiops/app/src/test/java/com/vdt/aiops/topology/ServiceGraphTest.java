package com.vdt.aiops.topology;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.vdt.aiops.topology.enums.ServiceRole;

/** Unit tests for the dependency graph: downstream/upstream inversion and role lookup. */
class ServiceGraphTest {

    // nginx -> node-api -> {postgres, redis}
    private ServiceGraph graph() {
        Map<String, Set<String>> downstream = new HashMap<>();
        downstream.put("nginx", Set.of("node-api"));
        downstream.put("node-api", Set.of("postgres", "redis"));

        Map<String, ServiceNode> nodes = new HashMap<>();
        nodes.put("nginx", new ServiceNode("nginx", "id-nginx", ServiceRole.PROXY));
        nodes.put("node-api", new ServiceNode("node-api", "id-api", ServiceRole.APP));
        nodes.put("postgres", new ServiceNode("postgres", "id-pg", ServiceRole.DATASTORE));
        nodes.put("redis", new ServiceNode("redis", "id-redis", ServiceRole.DATASTORE));
        return new ServiceGraph(nodes, downstream);
    }

    @Test
    void downstreamReturnsConfiguredDependencies() {
        assertThat(graph().downstream("node-api")).containsExactlyInAnyOrder("postgres", "redis");
    }

    @Test
    void upstreamIsTheInverseOfDownstream() {
        ServiceGraph g = graph();
        assertThat(g.upstream("postgres")).containsExactly("node-api");
        assertThat(g.upstream("redis")).containsExactly("node-api");
        assertThat(g.upstream("node-api")).containsExactly("nginx");
    }

    @Test
    void leafDependencyHasNoDownstream() {
        assertThat(graph().downstream("postgres")).isEmpty();
    }

    @Test
    void rootServiceHasNoUpstream() {
        assertThat(graph().upstream("nginx")).isEmpty();
    }

    @Test
    void unknownServiceReturnsEmptySets() {
        ServiceGraph g = graph();
        assertThat(g.downstream("does-not-exist")).isEmpty();
        assertThat(g.upstream("does-not-exist")).isEmpty();
    }

    @Test
    void roleReturnsNodeRoleOrUnknown() {
        ServiceGraph g = graph();
        assertThat(g.role("node-api")).isEqualTo(ServiceRole.APP);
        assertThat(g.role("postgres")).isEqualTo(ServiceRole.DATASTORE);
        assertThat(g.role("does-not-exist")).isEqualTo(ServiceRole.UNKNOWN);
    }

    @Test
    void allNodesExposesEveryService() {
        assertThat(graph().allNodes()).hasSize(4);
        assertThat(graph().allNodes().stream().map(ServiceNode::getName).toList())
                .containsExactlyInAnyOrderElementsOf(List.of("nginx", "node-api", "postgres", "redis"));
    }
}
