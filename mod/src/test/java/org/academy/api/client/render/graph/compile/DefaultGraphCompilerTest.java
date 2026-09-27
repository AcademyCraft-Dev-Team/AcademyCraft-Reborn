package org.academy.api.client.render.graph.compile;

import org.academy.api.client.render.graph.GraphFixtures;
import org.academy.api.client.render.graph.model.Edge;
import org.academy.api.client.render.graph.model.Graph;
import org.academy.api.client.render.graph.model.GraphNode;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.graph.type.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DefaultGraphCompilerTest {
    private final SimpleNodeRegistry registry = new SimpleNodeRegistry();

    private DefaultGraphCompiler compiler() {
        return new DefaultGraphCompiler(registry);
    }

    /**
     * 测试求值器：折叠 constant（读 value 属性）与 add（a+b）。
     */
    private static NodeEvaluator arithmeticEvaluator() {
        return (node, inputs) -> switch (node.type()) {
            case "input.constant" -> Optional.of(Map.of("out", Value.of(
                    Float.parseFloat(node.properties().get("value")))));
            case "math.add" -> Optional.of(Map.of("out", Value.of(
                    inputs.get("a").asFloat() + inputs.get("b").asFloat())));
            default -> Optional.empty();
        };
    }

    @Test
    void compilesValidGraphInTopologicalOrder() {
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

        var compiled = compiler().compile(graph);
        assertEquals(List.of("n1", "n2"), compiled.execOrder().stream().map(GraphNode::id).toList());
    }

    @Test
    void throwsOnInvalidGraph() {
        var graph = new Graph("g",
                List.of(GraphFixtures.node(GraphFixtures.addType(), "n1", 0f, 0f)),
                List.of(), List.of(), List.of("n1"));

        assertThrows(GraphCompileException.class, () -> compiler().compile(graph));
    }

    @Test
    void throwsOnCycle() {
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

        assertThrows(GraphCompileException.class, () -> compiler().compile(graph));
    }

    @Test
    void eliminatesDeadNodes() {
        registry.register(GraphFixtures.addType());
        var graph = new Graph(
                "g",
                List.of(
                        GraphFixtures.node(GraphFixtures.addType(), "n1", 0f, 0f),
                        GraphFixtures.node(GraphFixtures.addType(), "n2", 1f, 1f),
                        GraphFixtures.node(GraphFixtures.addType(), "dead", 2f, 2f)
                ),
                List.of(new Edge(new Edge.PortRef("n1", "out"), new Edge.PortRef("n2", "a"))),
                List.of(),
                List.of("n2")
        );

        var compiled = compiler().compile(graph);
        assertEquals(List.of("n1", "n2"), compiled.execOrder().stream().map(GraphNode::id).toList());
    }

    @Test
    void foldsConstantSubgraph() {
        registry.register(GraphFixtures.addType());
        registry.register(GraphFixtures.constantType());
        var compiler = new DefaultGraphCompiler(registry, arithmeticEvaluator());

        var graph = new Graph(
                "g",
                List.of(
                        GraphFixtures.node(GraphFixtures.constantType(), "c1", Map.of("value", "2.0"), 0f, 0f),
                        GraphFixtures.node(GraphFixtures.constantType(), "c2", Map.of("value", "3.0"), 1f, 1f),
                        GraphFixtures.node(GraphFixtures.addType(), "sum", 2f, 2f),
                        GraphFixtures.node(GraphFixtures.addType(), "out", 3f, 3f)
                ),
                List.of(
                        new Edge(new Edge.PortRef("c1", "out"), new Edge.PortRef("sum", "a")),
                        new Edge(new Edge.PortRef("c2", "out"), new Edge.PortRef("sum", "b")),
                        new Edge(new Edge.PortRef("sum", "out"), new Edge.PortRef("out", "a")),
                        new Edge(new Edge.PortRef("c1", "out"), new Edge.PortRef("out", "b"))
                ),
                List.of(),
                List.of("out")
        );

        var compiled = compiler.compile(graph);

        assertEquals(List.of("out"), compiled.execOrder().stream().map(GraphNode::id).toList());
        assertEquals(2.0f, compiled.foldedOutputs().get("c1").get("out").asFloat());
        assertEquals(3.0f, compiled.foldedOutputs().get("c2").get("out").asFloat());
        assertEquals(5.0f, compiled.foldedOutputs().get("sum").get("out").asFloat());
    }

    @Test
    void withoutEvaluatorDoesNotFold() {
        registry.register(GraphFixtures.addType());
        registry.register(GraphFixtures.constantType());
        var compiler = new DefaultGraphCompiler(registry);

        var graph = new Graph(
                "g",
                List.of(GraphFixtures.node(GraphFixtures.constantType(), "c1", Map.of("value", "2.0"), 0f, 0f)),
                List.of(),
                List.of(),
                List.of("c1")
        );

        var compiled = compiler.compile(graph);
        assertTrue(compiled.foldedOutputs().isEmpty());
        assertEquals(1, compiled.execOrder().size());
    }
}
