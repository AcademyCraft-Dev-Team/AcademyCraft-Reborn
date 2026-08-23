package org.academy.api.client.render.shader.codegen;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.academy.api.client.render.graph.GraphFixtures;
import org.academy.api.client.render.graph.compile.DefaultGraphCompiler;
import org.academy.api.client.render.graph.model.Edge;
import org.academy.api.client.render.graph.model.Graph;
import org.academy.api.client.render.graph.model.GraphNode;
import org.academy.api.client.render.graph.model.GraphParameter;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;
import org.academy.api.client.render.shader.nodes.ShaderNodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GlslGeneratorTest {
    private SimpleNodeRegistry registry;
    private GlslNodeRegistry glslRegistry;

    @BeforeEach
    void setUp() {
        registry = new SimpleNodeRegistry();
        glslRegistry = new GlslNodeRegistry();
        ShaderNodes.registerAll(registry, glslRegistry);
    }

    private GraphNode node(String typeId, String id, Map<String, String> props) {
        return GraphFixtures.node(registry.find(typeId), id, props, 0f, 0f);
    }

    private String generate(Graph graph) {
        var compiled = new DefaultGraphCompiler(registry).compile(graph);
        return new GlslGenerator(glslRegistry).generate(graph, compiled).fragmentSource();
    }

    @Test
    void generatesFragmentShaderForAddGraph() {
        var graph = new Graph(
                "g",
                List.of(
                        node("input.constant", "c1", Map.of("value", "2.0")),
                        node("input.constant", "c2", Map.of("value", "3.0")),
                        node("math.add", "sum", Map.of()),
                        node("output.color", "out", Map.of())
                ),
                List.of(
                        new Edge(new Edge.PortRef("c1", "out"), new Edge.PortRef("sum", "a")),
                        new Edge(new Edge.PortRef("c2", "out"), new Edge.PortRef("sum", "b")),
                        new Edge(new Edge.PortRef("sum", "out"), new Edge.PortRef("out", "color"))
                ),
                List.of(),
                List.of("out")
        );

        var source = generate(graph);

        assertTrue(source.startsWith("#version 330"));
        assertTrue(source.contains("layout(std140) uniform GraphUniforms {"));
        assertTrue(source.contains("out vec4 fragColor;"));
        assertTrue(source.contains("float v_c1_out = 2.0;"));
        assertTrue(source.contains("float v_sum_out = (v_c1_out + v_c2_out);"));
        assertTrue(source.contains("fragColor = vec4(v_sum_out);"));
    }

    @Test
    void emitsUniformsForParameters() {
        var graph = new Graph(
                "g",
                List.of(
                        node("input.param_float", "p1", Map.of("param", "speed")),
                        node("output.color", "out", Map.of())
                ),
                List.of(new Edge(new Edge.PortRef("p1", "out"), new Edge.PortRef("out", "color"))),
                List.of(new GraphParameter("speed", "Speed", ValueType.FLOAT, Value.of(1f), java.util.Optional.empty())),
                List.of("out")
        );

        var source = generate(graph);

        assertTrue(source.contains("float u_speed;"));
        assertTrue(source.contains("float v_p1_out = u_speed;"));
    }

    @Test
    void emitsNoiseHelperWhenUsed() {
        var graph = new Graph(
                "g",
                List.of(
                        node("input.uv", "uv", Map.of()),
                        node("math.noise", "n", Map.of()),
                        node("output.color", "out", Map.of())
                ),
                List.of(
                        new Edge(new Edge.PortRef("uv", "out"), new Edge.PortRef("n", "uv")),
                        new Edge(new Edge.PortRef("n", "out"), new Edge.PortRef("out", "color"))
                ),
                List.of(),
                List.of("out")
        );

        var source = generate(graph);

        assertTrue(source.contains("_academy_noise"));
        assertTrue(source.contains("float v_n_out = _academy_noise(v_uv_out);"));
    }

    @Test
    void noSamplerDeclaredWhenNoTextures() {
        var graph = new Graph(
                "g",
                List.of(
                        node("input.constant", "c1", Map.of("value", "1.0")),
                        node("output.color", "out", Map.of())
                ),
                List.of(new Edge(new Edge.PortRef("c1", "out"), new Edge.PortRef("out", "color"))),
                List.of(),
                List.of("out")
        );

        assertTrue(!generate(graph).contains("uniform sampler2D"));
    }

    @Test
    void multiSamplerDeclarationsAndResolution() {
        var graph = new Graph(
                "g",
                List.of(
                        node("texture.sample", "t1", Map.of("texture", "minecraft:textures/block/stone.png")),
                        node("texture.sample", "t2", Map.of("texture", "minecraft:textures/block/dirt.png")),
                        node("color.blend", "blend", Map.of()),
                        node("output.color", "out", Map.of())
                ),
                List.of(
                        new Edge(new Edge.PortRef("t1", "rgba"), new Edge.PortRef("blend", "a")),
                        new Edge(new Edge.PortRef("t2", "rgba"), new Edge.PortRef("blend", "b")),
                        new Edge(new Edge.PortRef("blend", "out"), new Edge.PortRef("out", "color"))
                ),
                List.of(),
                List.of("out")
        );

        var source = generate(graph);

        assertTrue(source.contains("uniform sampler2D Sampler0;"));
        assertTrue(source.contains("uniform sampler2D Sampler1;"));
        assertTrue(source.contains("texture(Sampler0,"));
        assertTrue(source.contains("texture(Sampler1,"));
    }

    @Test
    void sameTextureReusesSlot() {
        var graph = new Graph(
                "g",
                List.of(
                        node("texture.sample", "t1", Map.of("texture", "minecraft:textures/block/stone.png")),
                        node("texture.sample", "t2", Map.of("texture", "minecraft:textures/block/stone.png")),
                        node("color.blend", "blend", Map.of()),
                        node("output.color", "out", Map.of())
                ),
                List.of(
                        new Edge(new Edge.PortRef("t1", "rgba"), new Edge.PortRef("blend", "a")),
                        new Edge(new Edge.PortRef("t2", "rgba"), new Edge.PortRef("blend", "b")),
                        new Edge(new Edge.PortRef("blend", "out"), new Edge.PortRef("out", "color"))
                ),
                List.of(),
                List.of("out")
        );

        var source = generate(graph);

        assertTrue(source.contains("uniform sampler2D Sampler0;"));
        assertTrue(!source.contains("uniform sampler2D Sampler1;"));
    }

    @Test
    void samplePlanAssignsSlotsInDeclarationOrder() {
        var graph = new Graph(
                "g",
                List.of(
                        node("texture.sample", "t1", Map.of("texture", "minecraft:textures/block/stone.png")),
                        node("texture.sample", "t2", Map.of("texture", "minecraft:textures/block/dirt.png"))
                ),
                List.of(),
                List.of(),
                List.of()
        );

        var plan = GlslGenerator.samplePlan(graph);
        assertTrue(plan.size() == 2);
        assertTrue("Sampler0".equals(plan.get(0).uniformName()));
        assertTrue("minecraft:textures/block/stone.png".equals(plan.get(0).identifier()));
        assertTrue("Sampler1".equals(plan.get(1).uniformName()));
        assertTrue("minecraft:textures/block/dirt.png".equals(plan.get(1).identifier()));
    }
}
