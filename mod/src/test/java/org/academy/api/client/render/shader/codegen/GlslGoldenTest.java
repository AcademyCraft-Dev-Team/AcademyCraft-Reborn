package org.academy.api.client.render.shader.codegen;

import org.academy.api.client.render.graph.GraphFixtures;
import org.academy.api.client.render.graph.compile.DefaultGraphCompiler;
import org.academy.api.client.render.graph.model.Edge;
import org.academy.api.client.render.graph.model.Graph;
import org.academy.api.client.render.graph.model.GraphNode;
import org.academy.api.client.render.graph.model.GraphParameter;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.graph.type.Curve;
import org.academy.api.client.render.graph.type.Gradient;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;
import org.academy.api.client.render.shader.nodes.ShaderNodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * M16-05 GLSL 黄金测试：代表性图 → 生成 GLSL → 与 {@code src/test/resources/shader/golden/*.glsl}
 * 精确快照比对。更新模式：{@code ./gradlew test -Dgolden.update=true}。
 *
 * <p>覆盖：math 链、texture.sample、curve/gradient、噪声、自定义函数。</p>
 */
class GlslGoldenTest {
    private static final Path GOLDEN_DIR = Path.of("src/test/resources/shader/golden");

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

    private void golden(String name, Graph graph) throws IOException {
        var source = generate(graph);
        var file = GOLDEN_DIR.resolve(name + ".glsl");
        var update = Boolean.parseBoolean(System.getProperty("golden.update", "false"));
        if (update) {
            Files.createDirectories(GOLDEN_DIR);
            Files.writeString(file, source);
            return;
        }
        var resource = "/shader/golden/" + name + ".glsl";
        var stream = getClass().getResourceAsStream(resource);
        assertNotNull(stream, "missing golden resource " + resource + " (run with -Dgolden.update=true)");
        var expected = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(normalizeLineEndings(expected), normalizeLineEndings(source), "golden mismatch for " + name);
    }

    private static String normalizeLineEndings(String source) {
        return source.replace("\r\n", "\n").replace('\r', '\n');
    }

    @Test
    void mathChain() throws IOException {
        var graph = new Graph(
                "math_chain",
                List.of(
                        node("input.constant", "c1", Map.of("value", "2.0")),
                        node("input.constant", "c2", Map.of("value", "3.0")),
                        node("math.add", "sum", Map.of()),
                        node("math.multiply", "prod", Map.of()),
                        node("output.color", "out", Map.of())
                ),
                List.of(
                        new Edge(new Edge.PortRef("c1", "out"), new Edge.PortRef("sum", "a")),
                        new Edge(new Edge.PortRef("c2", "out"), new Edge.PortRef("sum", "b")),
                        new Edge(new Edge.PortRef("sum", "out"), new Edge.PortRef("prod", "a")),
                        new Edge(new Edge.PortRef("c2", "out"), new Edge.PortRef("prod", "b")),
                        new Edge(new Edge.PortRef("prod", "out"), new Edge.PortRef("out", "color"))
                ),
                List.of(),
                List.of("out")
        );
        golden("math_chain", graph);
    }

    @Test
    void textureSample() throws IOException {
        var graph = new Graph(
                "texture_sample",
                List.of(
                        node("input.uv", "uv", Map.of()),
                        node("texture.sample", "tex", Map.of("texture", "")),
                        node("output.color", "out", Map.of())
                ),
                List.of(
                        new Edge(new Edge.PortRef("uv", "out"), new Edge.PortRef("tex", "uv")),
                        new Edge(new Edge.PortRef("tex", "rgba"), new Edge.PortRef("out", "color"))
                ),
                List.of(),
                List.of("out")
        );
        golden("texture_sample", graph);
    }

    @Test
    void curveAndGradient() throws IOException {
        var curve = new Curve(List.of(
                new Curve.Keyframe(0f, 0f, 0f, 0f, Curve.Interpolation.LINEAR),
                new Curve.Keyframe(1f, 1f, 0f, 0f, Curve.Interpolation.SMOOTH)
        ));
        var gradient = new Gradient(List.of(
                new Gradient.ColorStop(0f, 1f, 0f, 0f, 1f),
                new Gradient.ColorStop(1f, 0f, 0f, 1f, 1f)
        ));
        var graph = new Graph(
                "curve_gradient",
                List.of(
                        node("input.uv", "uv", Map.of()),
                        node("curve.sample", "cs", Map.of("curve", "life")),
                        node("gradient.sample", "gs", Map.of("gradient", "col")),
                        node("output.color", "out", Map.of())
                ),
                List.of(
                        new Edge(new Edge.PortRef("uv", "out"), new Edge.PortRef("cs", "t")),
                        new Edge(new Edge.PortRef("uv", "out"), new Edge.PortRef("gs", "t")),
                        new Edge(new Edge.PortRef("cs", "out"), new Edge.PortRef("out", "color")),
                        new Edge(new Edge.PortRef("gs", "out"), new Edge.PortRef("out", "color"))
                ),
                List.of(
                        new GraphParameter("life", "Life", ValueType.CURVE, Value.curve(curve), Optional.empty()),
                        new GraphParameter("col", "Color", ValueType.GRADIENT, Value.gradient(gradient), Optional.empty())
                ),
                List.of("out")
        );
        golden("curve_gradient", graph);
    }

    @Test
    void noise() throws IOException {
        var graph = new Graph(
                "noise",
                List.of(
                        node("input.uv", "uv", Map.of()),
                        node("noise.perlin", "n", Map.of()),
                        node("output.color", "out", Map.of())
                ),
                List.of(
                        new Edge(new Edge.PortRef("uv", "out"), new Edge.PortRef("n", "uv")),
                        new Edge(new Edge.PortRef("n", "out"), new Edge.PortRef("out", "color"))
                ),
                List.of(),
                List.of("out")
        );
        golden("noise", graph);
    }

    @Test
    void customFunction() throws IOException {
        var graph = new Graph(
                "custom",
                List.of(
                        node("output.custom", "fn", Map.of("body", "vec4(0.25)")),
                        node("output.color", "out", Map.of())
                ),
                List.of(
                        new Edge(new Edge.PortRef("fn", "out"), new Edge.PortRef("out", "color"))
                ),
                List.of(),
                List.of("out")
        );
        golden("custom", graph);
    }

    @Test
    void parameterUniform() throws IOException {
        var graph = new Graph(
                "parameter",
                List.of(
                        node("input.param_float", "p", Map.of("param", "speed")),
                        node("math.saturate", "sat", Map.of()),
                        node("output.color", "out", Map.of())
                ),
                List.of(
                        new Edge(new Edge.PortRef("p", "out"), new Edge.PortRef("sat", "x")),
                        new Edge(new Edge.PortRef("sat", "out"), new Edge.PortRef("out", "color"))
                ),
                List.of(new GraphParameter("speed", "Speed", ValueType.FLOAT, Value.of(1f), Optional.empty())),
                List.of("out")
        );
        golden("parameter", graph);
    }
}
