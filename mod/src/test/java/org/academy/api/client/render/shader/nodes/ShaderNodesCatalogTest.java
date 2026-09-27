package org.academy.api.client.render.shader.nodes;

import org.academy.api.client.render.graph.GraphFixtures;
import org.academy.api.client.render.graph.compile.DefaultGraphCompiler;
import org.academy.api.client.render.graph.model.Edge;
import org.academy.api.client.render.graph.model.Graph;
import org.academy.api.client.render.graph.model.PortDirection;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.shader.codegen.GlslGenerator;
import org.academy.api.client.render.shader.codegen.GlslNodeRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M11 节点目录 codegen 测试：每个新增节点产出含预期 GLSL 的片段着色器。
 */
class ShaderNodesCatalogTest {
    private SimpleNodeRegistry registry;
    private GlslNodeRegistry glslRegistry;

    @BeforeEach
    void setUp() {
        registry = new SimpleNodeRegistry();
        glslRegistry = new GlslNodeRegistry();
        ShaderNodes.registerAll(registry, glslRegistry);
    }

    private String singleNode(String typeId, Map<String, String> props) {
        var nodeType = registry.find(typeId);
        var node = GraphFixtures.node(nodeType, "n", props, 0f, 0f);
        var out = GraphFixtures.node(registry.find("output.color"), "out", Map.of(), 0f, 0f);
        var outPort = nodeType.ports().stream()
                .filter(p -> p.direction() == PortDirection.OUTPUT).findFirst().orElseThrow();
        var edges = new ArrayList<Edge>();
        edges.add(new Edge(new Edge.PortRef("n", outPort.id()), new Edge.PortRef("out", "color")));
        var graph = new Graph("g", List.of(node, out), edges, List.of(), List.of("out"));
        var compiled = new DefaultGraphCompiler(registry).compile(graph);
        return new GlslGenerator(glslRegistry).generate(graph, compiled).fragmentSource();
    }

    private void assertEmits(String typeId, String fragment) {
        assertTrue(singleNode(typeId, Map.of()).contains(fragment), typeId + " should emit " + fragment);
    }

    @Test
    void samplerUniformDeclaredOnlyWhenUsed() {
        // 无纹理图：不声明任何 sampler
        assertFalse(singleNode("input.uv", Map.of()).contains("uniform sampler2D"));
        // 有 texture.sample：声明该纹理的槽位
        assertTrue(singleNode("texture.sample", Map.of("texture", "minecraft:textures/block/stone.png"))
                .contains("uniform sampler2D Sampler0;"));
    }

    @Test
    void mathNodesEmitExpectedFunctions() {
        assertEmits("math.tan", "tan(");
        assertEmits("math.asin", "asin(");
        assertEmits("math.acos", "acos(");
        assertEmits("math.atan", "atan(");
        assertEmits("math.atan2", "atan(");
        assertEmits("math.degrees", "degrees(");
        assertEmits("math.radians", "radians(");
        assertEmits("math.mod", "mod(");
        assertEmits("math.frac", "fract(");
        assertEmits("math.reciprocal", "(1.0 / ");
        assertEmits("math.abs", "abs(");
        assertEmits("math.sign", "sign(");
        assertEmits("math.floor", "floor(");
        assertEmits("math.ceil", "ceil(");
        assertEmits("math.round", "round(");
        assertEmits("math.trunc", "trunc(");
        assertEmits("math.sqrt", "sqrt(");
        assertEmits("math.exp", "exp(");
        assertEmits("math.log", "log(");
        assertEmits("math.exp2", "exp2(");
        assertEmits("math.log2", "log2(");
        assertEmits("math.min", "min(");
        assertEmits("math.max", "max(");
        assertEmits("math.saturate", "clamp(");
        assertEmits("math.smoothstep", "smoothstep(");
        assertEmits("math.step", "step(");
        assertEmits("math.remap", "clamp(");
        assertEmits("math.inverse_lerp", "/");
        assertEmits("math.cross", "cross(");
        assertEmits("math.distance", "distance(");
        assertEmits("math.reflect", "reflect(");
        assertEmits("math.refract", "refract(");
    }

    @Test
    void noiseNodesEmitHelpers() {
        assertEmits("noise.value", "_academy_value_noise(");
        assertEmits("noise.perlin", "_academy_perlin_noise(");
        assertEmits("noise.simplex", "_academy_simplex_noise(");
        assertEmits("noise.voronoi", "_academy_voronoi(");
    }

    @Test
    void colorNodesEmitExpectedFunctions() {
        assertEmits("color.hsv2rgb", "_academy_hsv2rgb(");
        assertEmits("color.rgb2hsv", "_academy_rgb2hsv(");
        assertEmits("color.contrast", ".rgb - 0.5) * ");
        assertEmits("color.luminance", "0.299");
        assertEmits("color.blend", "mix(");
        assertEmits("color.ramp", "mix(");
    }

    @Test
    void textureSampleEmitsTextureCall() {
        assertTrue(singleNode("texture.sample", Map.of("texture", "minecraft:textures/block/stone.png"))
                .contains("texture(Sampler0, "));
    }

    @Test
    void curveAndGradientSampleNodesEmitHelpers() {
        // 无对应参数时回退默认值，不崩
        assertTrue(singleNode("curve.sample", Map.of("curve", "missing")).contains("0.0"));
        assertTrue(singleNode("gradient.sample", Map.of("gradient", "missing")).contains("vec4(1.0)"));
    }

    @Test
    void coordinateNodesEmitApproximations() {
        assertEmits("input.world_pos", "texCoord * 2.0");
        assertEmits("input.object_pos", "texCoord * 2.0");
        assertEmits("input.normal", "vec3(0.0, 0.0, 1.0)");
        assertEmits("input.view_dir", "vec3(0.0, 0.0, 1.0)");
        assertEmits("input.camera_pos", "vec3(0.0, 0.0, 1.0)");
        assertEmits("input.screen_pos", "vec4(texCoord");
        assertEmits("input.sine_time", "sin(");
        assertEmits("input.cosine_time", "cos(");
        assertEmits("input.uv_tiling", "*");
    }

    @Test
    void customFunctionNodeEmitsBody() {
        assertTrue(singleNode("output.custom", Map.of("body", "vec4(1.0, 0.0, 0.0, 1.0)"))
                .contains("vec4(1.0, 0.0, 0.0, 1.0)"));
    }

    @Test
    void catalogSizeAtLeast80() {
        assertTrue(registry.all().size() >= 80, "expected >= 80 shader nodes, got " + registry.all().size());
    }
}
