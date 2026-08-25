package org.academy.api.client.render.shader.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.academy.api.client.render.graph.GraphFixtures;
import org.academy.api.client.render.graph.compile.DefaultGraphCompiler;
import org.academy.api.client.render.graph.model.Edge;
import org.academy.api.client.render.graph.model.Graph;
import org.academy.api.client.render.graph.model.GraphNode;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.shader.codegen.GlslNodeRegistry;
import org.academy.api.client.render.shader.nodes.ShaderNodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ShaderGraphPipelineTest {
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

    @Test
    void compileProducesPipelineAndLayout() {
        var graph = new Graph(
                "g",
                List.of(
                        node("input.constant", "c", Map.of("value", "1.0")),
                        node("output.color", "out", Map.of())
                ),
                List.of(new Edge(new Edge.PortRef("c", "out"), new Edge.PortRef("out", "color"))),
                List.of(),
                List.of("out")
        );
        var compiled = new DefaultGraphCompiler(registry).compile(graph);
        var pipeline = new ShaderGraphPipeline(glslRegistry);
        var result = pipeline.compile(graph, compiled);

        assertNotNull(result.pipeline());
        assertNotNull(result.layout());
        assertEquals("Time", result.layout().entries().get(0).name());
        assertTrue(result.pipeline().getLocation() != null);
    }

    @Test
    void shaderGraphResultCarriesPipelineAndLayout() {
        var graph = new Graph(
                "g",
                List.of(
                        node("input.constant", "c", Map.of("value", "1.0")),
                        node("output.color", "out", Map.of())
                ),
                List.of(new Edge(new Edge.PortRef("c", "out"), new Edge.PortRef("out", "color"))),
                List.of(),
                List.of("out")
        );
        var compiled = new DefaultGraphCompiler(registry).compile(graph);
        var result = new ShaderGraphPipeline(glslRegistry).compile(graph, compiled);
        assertNotNull(result.pipeline());
        assertNotNull(result.layout());
        // 无参数图：布局仅 Time 成员，std140 对齐到 16
        assertEquals(1, result.layout().entries().size());
        assertEquals(16, result.layout().totalSize());
    }

    @Test
    void bindGroupLayoutTracksSamplerCount() {
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
        var compiled = new DefaultGraphCompiler(registry).compile(graph);
        var result = new ShaderGraphPipeline(glslRegistry).compile(graph, compiled);

        assertEquals(2, result.layout().samplers().size());
        var samplerGroup = result.pipeline().getBindGroupLayouts().stream()
                .filter(g -> !g.getSamplers().isEmpty())
                .findFirst().orElseThrow();
        assertEquals(List.of("Sampler0", "Sampler1"), samplerGroup.getSamplers());
    }
}
