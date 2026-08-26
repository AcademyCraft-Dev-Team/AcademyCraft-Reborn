package org.academy.api.client.render.graph.validate;

import org.academy.api.client.render.graph.GraphFixtures;
import org.academy.api.client.render.graph.model.Edge;
import org.academy.api.client.render.graph.model.Graph;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphValidatorTest {
    private final SimpleNodeRegistry registry = new SimpleNodeRegistry();

    private DefaultGraphValidator validator() {
        return new DefaultGraphValidator(registry);
    }

    @Test
    void validGraphHasNoErrors() {
        registry.register(GraphFixtures.addType());
        var graph = new Graph(
                "g",
                List.of(
                        GraphFixtures.node(GraphFixtures.addType(), "n1", 0f, 0f),
                        GraphFixtures.node(GraphFixtures.addType(), "n2", 1f, 1f)
                ),
                List.of(new Edge(new Edge.PortRef("n1", "out"), new Edge.PortRef("n2", "a"))),
                List.of(),
                List.of("n2")
        );

        assertTrue(validator().validate(graph).isEmpty());
    }

    @Test
    void unknownNodeTypeIsReported() {
        var graph = new Graph("g",
                List.of(GraphFixtures.node(GraphFixtures.addType(), "n1", 0f, 0f)),
                List.of(), List.of(), List.of("n1"));

        var issues = validator().validate(graph);
        assertTrue(issues.stream().anyMatch(i -> i.message().contains("unknown node type")));
    }

    @Test
    void typeMismatchEdgeIsReported() {
        registry.register(GraphFixtures.addType());
        registry.register(GraphFixtures.samplerInputType());

        var graph = new Graph(
                "g",
                List.of(
                        GraphFixtures.node(GraphFixtures.addType(), "n1", 0f, 0f),
                        GraphFixtures.node(GraphFixtures.samplerInputType(), "n2", 1f, 1f)
                ),
                List.of(new Edge(new Edge.PortRef("n1", "out"), new Edge.PortRef("n2", "tex"))),
                List.of(),
                List.of("n2")
        );

        var issues = validator().validate(graph);
        assertTrue(issues.stream().anyMatch(i -> i.message().contains("incompatible types")));
    }

    @Test
    void cycleIsReported() {
        registry.register(GraphFixtures.addType());
        var graph = new Graph(
                "g",
                List.of(
                        GraphFixtures.node(GraphFixtures.addType(), "n1", 0f, 0f),
                        GraphFixtures.node(GraphFixtures.addType(), "n2", 1f, 1f)
                ),
                List.of(
                        new Edge(new Edge.PortRef("n1", "out"), new Edge.PortRef("n2", "a")),
                        new Edge(new Edge.PortRef("n2", "out"), new Edge.PortRef("n1", "a"))
                ),
                List.of(),
                List.of("n2")
        );

        var issues = validator().validate(graph);
        assertTrue(issues.stream().anyMatch(i -> i.message().contains("cycle")));
    }

    @Test
    void missingOutputIsReported() {
        registry.register(GraphFixtures.addType());
        var graph = new Graph("g",
                List.of(GraphFixtures.node(GraphFixtures.addType(), "n1", 0f, 0f)),
                List.of(), List.of(), List.of("missing"));

        var issues = validator().validate(graph);
        assertTrue(issues.stream().anyMatch(i -> i.message().contains("missing node")));
    }

    @Test
    void graphWithoutOutputsIsReported() {
        registry.register(GraphFixtures.addType());
        var graph = new Graph("g",
                List.of(GraphFixtures.node(GraphFixtures.addType(), "n1", 0f, 0f)),
                List.of(), List.of(), List.of());

        var issues = validator().validate(graph);
        assertTrue(issues.stream().anyMatch(i -> i.message().contains("no output")));
    }
}
