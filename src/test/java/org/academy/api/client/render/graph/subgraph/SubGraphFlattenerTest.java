package org.academy.api.client.render.graph.subgraph;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.academy.api.client.render.graph.GraphFixtures;
import org.academy.api.client.render.graph.compile.DefaultGraphCompiler;
import org.academy.api.client.render.graph.model.Edge;
import org.academy.api.client.render.graph.model.Graph;
import org.academy.api.client.render.graph.model.GraphParameter;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;
import org.academy.api.client.render.shader.codegen.GlslGenerator;
import org.academy.api.client.render.shader.codegen.GlslNodeRegistry;
import org.academy.api.client.render.shader.nodes.ShaderNodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SubGraphFlattenerTest {
    private SimpleNodeRegistry registry;
    private GlslNodeRegistry glslRegistry;

    @BeforeEach
    void setUp() {
        registry = new SimpleNodeRegistry();
        glslRegistry = new GlslNodeRegistry();
        ShaderNodes.registerAll(registry, glslRegistry);
    }

    private Graph subGraph() {
        return new Graph(
                "sub",
                List.of(
                        GraphFixtures.node(registry.find("input.param_float"), "p1", Map.of("param", "x"), 0f, 0f),
                        GraphFixtures.node(registry.find("output.color"), "o", Map.of(), 0f, 0f)
                ),
                List.of(new Edge(new Edge.PortRef("p1", "out"), new Edge.PortRef("o", "color"))),
                List.of(new GraphParameter("x", "X", ValueType.FLOAT, Value.of(2f), Optional.empty())),
                List.of("o")
        );
    }

    @Test
    void overriddenParameterFeedsParentValue() {
        var registry_ = new SubGraphRegistry();
        registry_.register("sub", subGraph());

        var parent = new Graph(
                "g",
                List.of(
                        GraphFixtures.node(registry.find("input.constant"), "c", Map.of("value", "5.0"), 0f, 0f),
                        GraphFixtures.node(registry.find("subgraph"), "s", Map.of("graph", "sub"), 0f, 0f),
                        GraphFixtures.node(registry.find("output.color"), "out", Map.of(), 0f, 0f)
                ),
                List.of(
                        new Edge(new Edge.PortRef("c", "out"), new Edge.PortRef("s", "in0")),
                        new Edge(new Edge.PortRef("s", "out"), new Edge.PortRef("out", "color"))
                ),
                List.of(),
                List.of("out")
        );

        var flat = SubGraphFlattener.flatten(parent, registry_);
        var compiled = new DefaultGraphCompiler(registry).compile(flat);
        var source = new GlslGenerator(glslRegistry).generate(flat, compiled).fragmentSource();
        // 参数被父图覆盖：常量 5.0 直达输出，无 u_x
        assertTrue(source.contains("float v_c_out = 5.0;"));
        assertTrue(source.contains("fragColor = vec4(v_c_out);"));
    }

    @Test
    void unoverriddenParameterIsPromoted() {
        var registry_ = new SubGraphRegistry();
        registry_.register("sub", subGraph());

        var parent = new Graph(
                "g",
                List.of(
                        GraphFixtures.node(registry.find("subgraph"), "s", Map.of("graph", "sub"), 0f, 0f),
                        GraphFixtures.node(registry.find("output.color"), "out", Map.of(), 0f, 0f)
                ),
                List.of(new Edge(new Edge.PortRef("s", "out"), new Edge.PortRef("out", "color"))),
                List.of(),
                List.of("out")
        );

        var flat = SubGraphFlattener.flatten(parent, registry_);
        var compiled = new DefaultGraphCompiler(registry).compile(flat);
        var source = new GlslGenerator(glslRegistry).generate(flat, compiled).fragmentSource();
        // 参数提升为顶层 uniform
        assertTrue(source.contains("float u_x;"));
        assertTrue(source.contains("float v_s_p1_out = u_x;"));
        assertTrue(source.contains("fragColor = vec4(v_s_p1_out);"));
    }

    @Test
    void missingSubGraphLeavesNodeIntact() {
        var registry_ = new SubGraphRegistry();
        var parent = new Graph(
                "g",
                List.of(
                        GraphFixtures.node(registry.find("subgraph"), "s", Map.of("graph", "nope"), 0f, 0f)
                ),
                List.of(),
                List.of(),
                List.of()
        );
        var flat = SubGraphFlattener.flatten(parent, registry_);
        assertTrue(flat.nodes().stream().anyMatch(n -> "subgraph".equals(n.type())));
    }
}
