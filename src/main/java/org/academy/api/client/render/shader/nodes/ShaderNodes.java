package org.academy.api.client.render.shader.nodes;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.academy.api.client.render.graph.model.GraphNode;
import org.academy.api.client.render.graph.model.PortDirection;
import org.academy.api.client.render.graph.registry.NodeRegistry;
import org.academy.api.client.render.graph.registry.NodeType;
import org.academy.api.client.render.graph.registry.PortSpec;
import org.academy.api.client.render.graph.registry.PropertySpec;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;
import org.academy.api.client.render.shader.codegen.CurveGradientGlsl;
import org.academy.api.client.render.shader.codegen.Expr;
import org.academy.api.client.render.shader.codegen.GlslGenerator;
import org.academy.api.client.render.shader.codegen.GlslLiterals;
import org.academy.api.client.render.shader.codegen.GlslNodeGenerator;
import org.academy.api.client.render.shader.codegen.GlslNodeRegistry;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Shader 节点目录：注册节点元数据（核心 NodeRegistry）与 GLSL 生成器（GlslNodeRegistry）。
 *
 * <p>纹理采样（M11-01/A1，ADR-021）：{@code texture.sample} 按 {@code texture} 属性经
 * {@link org.academy.api.client.render.shader.codegen.GlslGenContext#samplerName} 解析为
 * {@code Sampler0..SamplerN-1}（多样本动态绑定）；坐标/几何输入（M11-07）目前为全屏 quad 预览的近似实现。</p>
 */
public final class ShaderNodes {
    private static final String NOISE_HELPER = """
            float _academy_noise(vec2 p) {
                return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
            }
            """;

    private static final String VALUE_NOISE_HELPER = """
            float _academy_hash(vec2 p) {
                return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
            }
            float _academy_value_noise(vec2 p) {
                vec2 i = floor(p);
                vec2 f = fract(p);
                vec2 u = f * f * (3.0 - 2.0 * f);
                return mix(mix(_academy_hash(i), _academy_hash(i + vec2(1.0, 0.0)), u.x),
                           mix(_academy_hash(i + vec2(0.0, 1.0)), _academy_hash(i + vec2(1.0, 1.0)), u.x), u.y);
            }
            """;

    private static final String PERLIN_HELPER = """
            float _academy_hash(vec2 p) {
                return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
            }
            vec2 _academy_perlin_grad(vec2 p) {
                float h = _academy_hash(p) * 6.28318530718;
                return vec2(cos(h), sin(h));
            }
            float _academy_perlin_noise(vec2 p) {
                vec2 i = floor(p);
                vec2 f = fract(p);
                vec2 u = f * f * (3.0 - 2.0 * f);
                vec2 g00 = _academy_perlin_grad(i);
                vec2 g10 = _academy_perlin_grad(i + vec2(1.0, 0.0));
                vec2 g01 = _academy_perlin_grad(i + vec2(0.0, 1.0));
                vec2 g11 = _academy_perlin_grad(i + vec2(1.0, 1.0));
                float v00 = dot(g00, f);
                float v10 = dot(g10, f - vec2(1.0, 0.0));
                float v01 = dot(g01, f - vec2(0.0, 1.0));
                float v11 = dot(g11, f - vec2(1.0, 1.0));
                return mix(mix(v00, v10, u.x), mix(v01, v11, u.x), u.y) * 0.5 + 0.5;
            }
            """;

    private static final String SIMPLEX_HELPER = """
            vec3 _academy_mod289(vec3 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
            vec3 _academy_permute(vec3 x) { return _academy_mod289(((x * 34.0) + 1.0) * x); }
            float _academy_simplex_noise(vec2 v) {
                const vec2 C = vec2(0.211324865405187, 0.366025403784439);
                vec2 i = floor(v + dot(v, C.yy));
                vec2 x0 = v - i + dot(i, C.xx);
                vec2 i1 = (x0.x > x0.y) ? vec2(1.0, 0.0) : vec2(0.0, 1.0);
                vec4 x12 = x0.xyxy + C.xxzz;
                x12.xy -= i1;
                i = _academy_mod289(i);
                vec3 p = _academy_permute(_academy_permute(i.y + vec3(0.0, i1.y, 1.0)) + i.x + vec3(0.0, i1.x, 1.0));
                vec3 m = max(0.5 - vec3(dot(x0, x0), dot(x12.xy, x12.xy), dot(x12.zw, x12.zw)), 0.0);
                m = m * m;
                m = m * m;
                vec3 x = 2.0 * fract(p * C.xxx) - 1.0;
                vec3 h = abs(x) - 0.5;
                vec3 ox = floor(x + 0.5);
                vec3 a0 = x - ox;
                m *= 1.79284291400159 - 0.85373472095314 * (a0 * a0 + h * h);
                vec3 g;
                g.x = a0.x * x0.x + h.x * x0.y;
                g.yz = a0.yz * x12.xz + h.yz * x12.yw;
                return 130.0 * dot(m, g);
            }
            """;

    private static final String VORONOI_HELPER = """
            float _academy_hash(vec2 p) {
                return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
            }
            float _academy_voronoi(vec2 p) {
                vec2 i = floor(p);
                vec2 f = fract(p);
                float md = 8.0;
                for (int x = -1; x <= 1; x++) {
                    for (int y = -1; y <= 1; y++) {
                        vec2 cell = vec2(float(x), float(y));
                        vec2 point = cell + vec2(_academy_hash(i + cell));
                        md = min(md, distance(f, point));
                    }
                }
                return md;
            }
            """;

    private static final String HSV_HELPER = """
            vec3 _academy_hsv2rgb(vec3 c) {
                vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
                vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
                return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
            }
            vec3 _academy_rgb2hsv(vec3 c) {
                vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
                vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
                vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
                float d = q.x - min(q.w, q.y);
                float e = 1.0e-10;
                return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
            }
            """;

    private ShaderNodes() {
    }

    public static void registerAll(NodeRegistry metadata, GlslNodeRegistry codegen) {
        register(metadata, codegen,
                type("input.constant", "input", "Constant",
                        List.of(out("out", "Out", ValueType.FLOAT)),
                        List.of(prop("value", "Value", ValueType.FLOAT, Value.of(0f)))),
                (n, i, c) -> Map.of("out", GlslLiterals.of(Value.of(parseFloat(n, "value")))));

        register(metadata, codegen,
                type("input.color", "input", "Color",
                        List.of(out("out", "Out", ValueType.COLOR)),
                        List.of(prop("value", "Value", ValueType.COLOR, Value.color(1f, 1f, 1f, 1f)))),
                (n, i, c) -> Map.of("out", colorLiteral(n.properties().getOrDefault("value", "1.0,1.0,1.0,1.0"))));

        register(metadata, codegen,
                type("input.time", "input", "Time", List.of(out("out", "Out", ValueType.FLOAT)), List.of()),
                (n, i, c) -> Map.of("out", new Expr(GlslGenerator.TIME_MEMBER, ValueType.FLOAT)));

        register(metadata, codegen,
                type("input.uv", "input", "UV", List.of(out("out", "Out", ValueType.VEC2)), List.of()),
                (n, i, c) -> Map.of("out", new Expr(GlslGenerator.UV_VARYING, ValueType.VEC2)));

        registerParam(metadata, codegen, "input.param_float", ValueType.FLOAT);
        registerParam(metadata, codegen, "input.param_vec3", ValueType.VEC3);
        registerParam(metadata, codegen, "input.param_color", ValueType.COLOR);

        // ---- 输入 / 坐标（M11-07，全屏 quad 预览近似）----

        register(metadata, codegen,
                type("input.world_pos", "input", "World Position",
                        List.of(out("out", "Out", ValueType.VEC3)), List.of()),
                (n, i, c) -> Map.of("out", new Expr("vec3(" + GlslGenerator.UV_VARYING + " * 2.0 - 1.0, 0.0)", ValueType.VEC3)));
        register(metadata, codegen,
                type("input.object_pos", "input", "Object Position",
                        List.of(out("out", "Out", ValueType.VEC3)), List.of()),
                (n, i, c) -> Map.of("out", new Expr("vec3(" + GlslGenerator.UV_VARYING + " * 2.0 - 1.0, 0.0)", ValueType.VEC3)));
        register(metadata, codegen,
                type("input.normal", "input", "Normal",
                        List.of(out("out", "Out", ValueType.VEC3)), List.of()),
                (n, i, c) -> Map.of("out", new Expr("vec3(0.0, 0.0, 1.0)", ValueType.VEC3)));
        register(metadata, codegen,
                type("input.view_dir", "input", "View Direction",
                        List.of(out("out", "Out", ValueType.VEC3)), List.of()),
                (n, i, c) -> Map.of("out", new Expr("vec3(0.0, 0.0, 1.0)", ValueType.VEC3)));
        register(metadata, codegen,
                type("input.camera_pos", "input", "Camera Position",
                        List.of(out("out", "Out", ValueType.VEC3)), List.of()),
                (n, i, c) -> Map.of("out", new Expr("vec3(0.0, 0.0, 1.0)", ValueType.VEC3)));
        register(metadata, codegen,
                type("input.screen_pos", "input", "Screen Position",
                        List.of(out("out", "Out", ValueType.VEC4)), List.of()),
                (n, i, c) -> Map.of("out", new Expr("vec4(" + GlslGenerator.UV_VARYING + ", 0.0, 1.0)", ValueType.VEC4)));
        register(metadata, codegen,
                type("input.delta_time", "input", "Delta Time",
                        List.of(out("out", "Out", ValueType.FLOAT)), List.of()),
                (n, i, c) -> Map.of("out", new Expr("0.016", ValueType.FLOAT)));
        register(metadata, codegen,
                type("input.sine_time", "input", "Sine Time",
                        List.of(out("out", "Out", ValueType.FLOAT)), List.of()),
                (n, i, c) -> Map.of("out", new Expr("sin(" + GlslGenerator.TIME_MEMBER + ")", ValueType.FLOAT)));
        register(metadata, codegen,
                type("input.cosine_time", "input", "Cosine Time",
                        List.of(out("out", "Out", ValueType.FLOAT)), List.of()),
                (n, i, c) -> Map.of("out", new Expr("cos(" + GlslGenerator.TIME_MEMBER + ")", ValueType.FLOAT)));

        // ---- 纹理采样（M11-01）----

        register(metadata, codegen,
                type("texture.sample", "texture", "Sample Texture 2D",
                        List.of(in("uv", "UV", ValueType.VEC2),
                                in("tiling", "Tiling", ValueType.VEC2, Value.of(new Vector2f(1f, 1f))),
                                in("offset", "Offset", ValueType.VEC2, Value.of(new Vector2f())),
                                out("rgba", "RGBA", ValueType.COLOR)),
                        List.of(prop("texture", "Texture", ValueType.STRING, Value.string("")))),
                (n, i, c) -> {
                    var tex = n.properties().getOrDefault("texture", "");
                    return Map.of("rgba", new Expr(
                            "texture(" + c.samplerName(tex) + ", " + i.get("uv").code() + " * " + i.get("tiling").code()
                                    + " + " + i.get("offset").code() + ")",
                            ValueType.COLOR));
                });

        // ---- 曲线 / 渐变采样（M12-02）----

        register(metadata, codegen,
                type("curve.sample", "curve", "Sample Curve",
                        List.of(in("t", "T", ValueType.FLOAT), out("out", "Out", ValueType.FLOAT)),
                        List.of(prop("curve", "Curve", ValueType.STRING, Value.string("")))),
                (n, i, c) -> {
                    var id = n.properties().getOrDefault("curve", "");
                    var curve = c.curve(id);
                    if (curve == null) {
                        return Map.of("out", new Expr("0.0", ValueType.FLOAT));
                    }
                    c.addHelper(CurveGradientGlsl.curveFunction(curve, id));
                    return Map.of("out", new Expr(CurveGradientGlsl.curveName(id) + "(" + i.get("t").code() + ")", ValueType.FLOAT));
                });
        register(metadata, codegen,
                type("gradient.sample", "gradient", "Sample Gradient",
                        List.of(in("t", "T", ValueType.FLOAT), out("out", "Out", ValueType.COLOR)),
                        List.of(prop("gradient", "Gradient", ValueType.STRING, Value.string("")))),
                (n, i, c) -> {
                    var id = n.properties().getOrDefault("gradient", "");
                    var gradient = c.gradient(id);
                    if (gradient == null) {
                        return Map.of("out", new Expr("vec4(1.0)", ValueType.COLOR));
                    }
                    c.addHelper(CurveGradientGlsl.gradientFunction(gradient, id));
                    return Map.of("out", new Expr(CurveGradientGlsl.gradientName(id) + "(" + i.get("t").code() + ")", ValueType.COLOR));
                });

        // ---- 基础数学 ----

        registerBinary(metadata, codegen, "math.add", "Add", "+");
        registerBinary(metadata, codegen, "math.subtract", "Subtract", "-");
        registerBinary(metadata, codegen, "math.multiply", "Multiply", "*");
        registerBinary(metadata, codegen, "math.divide", "Divide", "/");
        register(metadata, codegen,
                type("math.power", "math", "Power",
                        List.of(in("a", "A", ValueType.FLOAT), in("b", "B", ValueType.FLOAT), out("out", "Out", ValueType.FLOAT)),
                        List.of()),
                (n, i, c) -> Map.of("out", new Expr("pow(" + i.get("a").code() + ", " + i.get("b").code() + ")", ValueType.FLOAT)));
        register(metadata, codegen,
                type("math.lerp", "math", "Lerp",
                        List.of(in("a", "A", ValueType.FLOAT), in("b", "B", ValueType.FLOAT), in("t", "T", ValueType.FLOAT), out("out", "Out", ValueType.FLOAT)),
                        List.of()),
                (n, i, c) -> Map.of("out", new Expr("mix(" + i.get("a").code() + ", " + i.get("b").code() + ", " + i.get("t").code() + ")", ValueType.FLOAT)));
        register(metadata, codegen,
                type("math.clamp", "math", "Clamp",
                        List.of(in("x", "X", ValueType.FLOAT), in("min", "Min", ValueType.FLOAT), in("max", "Max", ValueType.FLOAT), out("out", "Out", ValueType.FLOAT)),
                        List.of()),
                (n, i, c) -> Map.of("out", new Expr("clamp(" + i.get("x").code() + ", " + i.get("min").code() + ", " + i.get("max").code() + ")", ValueType.FLOAT)));
        registerUnaryFloat(metadata, codegen, "math.sin", "Sine", "sin");
        registerUnaryFloat(metadata, codegen, "math.cos", "Cosine", "cos");
        registerUnaryFloat(metadata, codegen, "math.tan", "Tangent", "tan");
        registerUnaryFloat(metadata, codegen, "math.asin", "Arcsine", "asin");
        registerUnaryFloat(metadata, codegen, "math.acos", "Arccosine", "acos");
        registerUnaryFloat(metadata, codegen, "math.atan", "Arctangent", "atan");
        registerBinaryFloat(metadata, codegen, "math.atan2", "Arctangent 2", "atan");
        registerUnaryFloat(metadata, codegen, "math.degrees", "Degrees", "degrees");
        registerUnaryFloat(metadata, codegen, "math.radians", "Radians", "radians");
        register(metadata, codegen,
                type("math.negate", "math", "Negate",
                        List.of(in("x", "X", ValueType.FLOAT), out("out", "Out", ValueType.FLOAT)), List.of()),
                (n, i, c) -> Map.of("out", new Expr("(-" + i.get("x").code() + ")", ValueType.FLOAT)));
        registerBinaryFloat(metadata, codegen, "math.mod", "Modulo", "mod");
        registerUnaryFloat(metadata, codegen, "math.frac", "Fraction", "fract");
        registerUnaryFloat(metadata, codegen, "math.reciprocal", "Reciprocal", null);
        registerUnaryFloat(metadata, codegen, "math.abs", "Absolute", "abs");
        registerUnaryFloat(metadata, codegen, "math.sign", "Sign", "sign");
        registerUnaryFloat(metadata, codegen, "math.floor", "Floor", "floor");
        registerUnaryFloat(metadata, codegen, "math.ceil", "Ceiling", "ceil");
        registerUnaryFloat(metadata, codegen, "math.round", "Round", "round");
        registerUnaryFloat(metadata, codegen, "math.trunc", "Truncate", "trunc");
        registerUnaryFloat(metadata, codegen, "math.sqrt", "Square Root", "sqrt");
        registerUnaryFloat(metadata, codegen, "math.exp", "Exponential", "exp");
        registerUnaryFloat(metadata, codegen, "math.log", "Logarithm", "log");
        registerUnaryFloat(metadata, codegen, "math.exp2", "Exponential 2", "exp2");
        registerUnaryFloat(metadata, codegen, "math.log2", "Logarithm 2", "log2");
        registerBinaryFloat(metadata, codegen, "math.min", "Minimum", "min");
        registerBinaryFloat(metadata, codegen, "math.max", "Maximum", "max");
        registerUnaryFloat(metadata, codegen, "math.saturate", "Saturate", "saturate");
        registerUnaryFloat(metadata, codegen, "math.smoothstep", "Smoothstep", null);
        register(metadata, codegen,
                type("math.step", "math", "Step",
                        List.of(in("edge", "Edge", ValueType.FLOAT), in("x", "X", ValueType.FLOAT), out("out", "Out", ValueType.FLOAT)),
                        List.of()),
                (n, i, c) -> Map.of("out", new Expr("step(" + i.get("edge").code() + ", " + i.get("x").code() + ")", ValueType.FLOAT)));
        register(metadata, codegen,
                type("math.remap", "math", "Remap",
                        List.of(in("x", "X", ValueType.FLOAT), in("inMin", "In Min", ValueType.FLOAT),
                                in("inMax", "In Max", ValueType.FLOAT), in("outMin", "Out Min", ValueType.FLOAT),
                                in("outMax", "Out Max", ValueType.FLOAT), out("out", "Out", ValueType.FLOAT)),
                        List.of()),
                (n, i, c) -> Map.of("out", new Expr("(" + i.get("outMin").code() + " + (" + i.get("outMax").code() + " - " + i.get("outMin").code() + ") * clamp((" + i.get("x").code() + " - " + i.get("inMin").code() + ") / (" + i.get("inMax").code() + " - " + i.get("inMin").code() + "), 0.0, 1.0))", ValueType.FLOAT)));
        register(metadata, codegen,
                type("math.inverse_lerp", "math", "Inverse Lerp",
                        List.of(in("a", "A", ValueType.FLOAT), in("b", "B", ValueType.FLOAT), in("x", "X", ValueType.FLOAT), out("out", "Out", ValueType.FLOAT)),
                        List.of()),
                (n, i, c) -> Map.of("out", new Expr("(" + i.get("x").code() + " - " + i.get("a").code() + ") / (" + i.get("b").code() + " - " + i.get("a").code() + ")", ValueType.FLOAT)));

        registerVec3Binary(metadata, codegen, "math.add_vec3", "Add Vec3", "+");
        registerVec3Binary(metadata, codegen, "math.multiply_vec3", "Multiply Vec3", "*");
        register(metadata, codegen,
                type("math.lerp_vec3", "math", "Lerp Vec3",
                        List.of(in("a", "A", ValueType.VEC3), in("b", "B", ValueType.VEC3), in("t", "T", ValueType.FLOAT), out("out", "Out", ValueType.VEC3)),
                        List.of()),
                (n, i, c) -> Map.of("out", new Expr("mix(" + i.get("a").code() + ", " + i.get("b").code() + ", " + i.get("t").code() + ")", ValueType.VEC3)));
        register(metadata, codegen,
                type("math.length", "math", "Length",
                        List.of(in("v", "V", ValueType.VEC3), out("out", "Out", ValueType.FLOAT)), List.of()),
                (n, i, c) -> Map.of("out", new Expr("length(" + i.get("v").code() + ")", ValueType.FLOAT)));
        register(metadata, codegen,
                type("math.normalize", "math", "Normalize",
                        List.of(in("v", "V", ValueType.VEC3), out("out", "Out", ValueType.VEC3)), List.of()),
                (n, i, c) -> Map.of("out", new Expr("normalize(" + i.get("v").code() + ")", ValueType.VEC3)));
        register(metadata, codegen,
                type("math.dot", "math", "Dot",
                        List.of(in("a", "A", ValueType.VEC3), in("b", "B", ValueType.VEC3), out("out", "Out", ValueType.FLOAT)),
                        List.of()),
                (n, i, c) -> Map.of("out", new Expr("dot(" + i.get("a").code() + ", " + i.get("b").code() + ")", ValueType.FLOAT)));
        register(metadata, codegen,
                type("math.cross", "math", "Cross",
                        List.of(in("a", "A", ValueType.VEC3), in("b", "B", ValueType.VEC3), out("out", "Out", ValueType.VEC3)),
                        List.of()),
                (n, i, c) -> Map.of("out", new Expr("cross(" + i.get("a").code() + ", " + i.get("b").code() + ")", ValueType.VEC3)));
        register(metadata, codegen,
                type("math.distance", "math", "Distance",
                        List.of(in("a", "A", ValueType.VEC3), in("b", "B", ValueType.VEC3), out("out", "Out", ValueType.FLOAT)),
                        List.of()),
                (n, i, c) -> Map.of("out", new Expr("distance(" + i.get("a").code() + ", " + i.get("b").code() + ")", ValueType.FLOAT)));
        register(metadata, codegen,
                type("math.reflect", "math", "Reflect",
                        List.of(in("i", "I", ValueType.VEC3), in("n", "N", ValueType.VEC3), out("out", "Out", ValueType.VEC3)),
                        List.of()),
                (n, i, c) -> Map.of("out", new Expr("reflect(" + i.get("i").code() + ", " + i.get("n").code() + ")", ValueType.VEC3)));
        register(metadata, codegen,
                type("math.refract", "math", "Refract",
                        List.of(in("i", "I", ValueType.VEC3), in("n", "N", ValueType.VEC3), in("eta", "Eta", ValueType.FLOAT), out("out", "Out", ValueType.VEC3)),
                        List.of()),
                (n, i, c) -> Map.of("out", new Expr("refract(" + i.get("i").code() + ", " + i.get("n").code() + ", " + i.get("eta").code() + ")", ValueType.VEC3)));

        // ---- 噪声（M11-06）----

        register(metadata, codegen,
                type("math.noise", "math", "Noise",
                        List.of(in("uv", "UV", ValueType.VEC2), out("out", "Out", ValueType.FLOAT)), List.of()),
                (n, i, c) -> {
                    c.addHelper(NOISE_HELPER);
                    return Map.of("out", new Expr("_academy_noise(" + i.get("uv").code() + ")", ValueType.FLOAT));
                });
        register(metadata, codegen,
                type("noise.value", "noise", "Value Noise",
                        List.of(in("uv", "UV", ValueType.VEC2), out("out", "Out", ValueType.FLOAT)), List.of()),
                (n, i, c) -> {
                    c.addHelper(VALUE_NOISE_HELPER);
                    return Map.of("out", new Expr("_academy_value_noise(" + i.get("uv").code() + ")", ValueType.FLOAT));
                });
        register(metadata, codegen,
                type("noise.perlin", "noise", "Perlin Noise",
                        List.of(in("uv", "UV", ValueType.VEC2), out("out", "Out", ValueType.FLOAT)), List.of()),
                (n, i, c) -> {
                    c.addHelper(PERLIN_HELPER);
                    return Map.of("out", new Expr("_academy_perlin_noise(" + i.get("uv").code() + ")", ValueType.FLOAT));
                });
        register(metadata, codegen,
                type("noise.simplex", "noise", "Simplex Noise",
                        List.of(in("uv", "UV", ValueType.VEC2), out("out", "Out", ValueType.FLOAT)), List.of()),
                (n, i, c) -> {
                    c.addHelper(SIMPLEX_HELPER);
                    return Map.of("out", new Expr("_academy_simplex_noise(" + i.get("uv").code() + ") * 0.5 + 0.5", ValueType.FLOAT));
                });
        register(metadata, codegen,
                type("noise.voronoi", "noise", "Voronoi",
                        List.of(in("uv", "UV", ValueType.VEC2), out("out", "Out", ValueType.FLOAT)), List.of()),
                (n, i, c) -> {
                    c.addHelper(VORONOI_HELPER);
                    return Map.of("out", new Expr("_academy_voronoi(" + i.get("uv").code() + ")", ValueType.FLOAT));
                });

        // ---- 颜色（M11-08）----

        register(metadata, codegen,
                type("color.ramp", "color", "Gradient Ramp",
                        List.of(in("t", "T", ValueType.FLOAT), out("out", "Out", ValueType.COLOR)),
                        List.of(prop("stops", "Stops", ValueType.STRING, Value.string("0.0:0,0,0,1;1.0:1,1,1,1")))),
                (n, i, c) -> Map.of("out", gradientExpr(n, i.get("t").code())));
        register(metadata, codegen,
                type("color.hsv2rgb", "color", "HSV to RGB",
                        List.of(in("h", "H", ValueType.FLOAT), in("s", "S", ValueType.FLOAT), in("v", "V", ValueType.FLOAT), out("out", "Out", ValueType.COLOR)),
                        List.of()),
                (n, i, c) -> {
                    c.addHelper(HSV_HELPER);
                    return Map.of("out", new Expr("vec4(_academy_hsv2rgb(vec3(" + i.get("h").code() + ", " + i.get("s").code() + ", " + i.get("v").code() + ")), 1.0)", ValueType.COLOR));
                });
        register(metadata, codegen,
                type("color.rgb2hsv", "color", "RGB to HSV",
                        List.of(in("c", "Color", ValueType.COLOR), out("h", "H", ValueType.FLOAT),
                                out("s", "S", ValueType.FLOAT), out("v", "V", ValueType.FLOAT)),
                        List.of()),
                (n, i, c) -> {
                    c.addHelper(HSV_HELPER);
                    var hsv = "_academy_rgb2hsv(" + i.get("c").code() + ".rgb)";
                    return Map.of(
                            "h", new Expr(hsv + ".x", ValueType.FLOAT),
                            "s", new Expr(hsv + ".y", ValueType.FLOAT),
                            "v", new Expr(hsv + ".z", ValueType.FLOAT));
                });
        register(metadata, codegen,
                type("color.contrast", "color", "Contrast",
                        List.of(in("color", "Color", ValueType.COLOR), in("contrast", "Contrast", ValueType.FLOAT, Value.of(1f)), out("out", "Out", ValueType.COLOR)),
                        List.of()),
                (n, i, c) -> Map.of("out", new Expr("vec4((" + i.get("color").code() + ".rgb - 0.5) * " + i.get("contrast").code() + " + 0.5, " + i.get("color").code() + ".a)", ValueType.COLOR)));
        register(metadata, codegen,
                type("color.luminance", "color", "Luminance",
                        List.of(in("color", "Color", ValueType.COLOR), out("out", "Out", ValueType.FLOAT)),
                        List.of()),
                (n, i, c) -> Map.of("out", new Expr("dot(" + i.get("color").code() + ".rgb, vec3(0.299, 0.587, 0.114))", ValueType.FLOAT)));
        register(metadata, codegen,
                type("color.blend", "color", "Blend",
                        List.of(in("a", "A", ValueType.COLOR), in("b", "B", ValueType.COLOR),
                                in("t", "T", ValueType.FLOAT, Value.of(0.5f)), out("out", "Out", ValueType.COLOR)),
                        List.of(prop("mode", "Mode", ValueType.STRING, Value.string("mix")))),
                (n, i, c) -> {
                    var a = i.get("a").code();
                    var b = i.get("b").code();
                    var expr = switch (n.properties().getOrDefault("mode", "mix")) {
                        case "multiply" -> "(" + a + " * " + b + ")";
                        case "screen" -> "(" + a + " + " + b + " - " + a + " * " + b + ")";
                        default -> "mix(" + a + ", " + b + ", " + i.get("t").code() + ")";
                    };
                    return Map.of("out", new Expr(expr, ValueType.COLOR));
                });

        // ---- UV 变换 ----

        register(metadata, codegen,
                type("input.uv_tiling", "input", "UV Tiling And Offset",
                        List.of(in("uv", "UV", ValueType.VEC2),
                                in("tiling", "Tiling", ValueType.VEC2, Value.of(new Vector2f(1f, 1f))),
                                in("offset", "Offset", ValueType.VEC2, Value.of(new Vector2f())),
                                out("out", "Out", ValueType.VEC2)),
                        List.of()),
                (n, i, c) -> Map.of("out", new Expr("(" + i.get("uv").code() + " * " + i.get("tiling").code() + " + " + i.get("offset").code() + ")", ValueType.VEC2)));

        // ---- 自定义函数（M11-09）----

        register(metadata, codegen,
                type("output.custom", "output", "Custom Function",
                        List.of(out("out", "Out", ValueType.COLOR)),
                        List.of(prop("body", "Body", ValueType.STRING, Value.string("vec4(0.0)")))),
                (n, i, c) -> Map.of("out", new Expr(n.properties().getOrDefault("body", "vec4(0.0)"), ValueType.COLOR)));

        // ---- 子图（M12-05）----
        // 端口由引用的子图动态派生（GraphEditorModel.portsFor）；编译期经 SubGraphFlattener 内联。

        metadata.register(type("subgraph", "subgraph", "Sub Graph",
                List.of(), List.of(prop("graph", "Graph", ValueType.STRING, Value.string("")))));

        register(metadata, codegen,
                type("combine.vec3", "combine", "Make Vec3",
                        List.of(in("x", "X", ValueType.FLOAT), in("y", "Y", ValueType.FLOAT), in("z", "Z", ValueType.FLOAT), out("out", "Out", ValueType.VEC3)),
                        List.of()),
                (n, i, c) -> Map.of("out", new Expr("vec3(" + i.get("x").code() + ", " + i.get("y").code() + ", " + i.get("z").code() + ")", ValueType.VEC3)));
        register(metadata, codegen,
                type("combine.vec4", "combine", "Make Vec4",
                        List.of(in("x", "X", ValueType.FLOAT), in("y", "Y", ValueType.FLOAT), in("z", "Z", ValueType.FLOAT), in("w", "W", ValueType.FLOAT), out("out", "Out", ValueType.VEC4)),
                        List.of()),
                (n, i, c) -> Map.of("out", new Expr("vec4(" + i.get("x").code() + ", " + i.get("y").code() + ", " + i.get("z").code() + ", " + i.get("w").code() + ")", ValueType.VEC4)));
        register(metadata, codegen,
                type("split.vec3", "split", "Split Vec3",
                        List.of(in("v", "V", ValueType.VEC3),
                                out("x", "X", ValueType.FLOAT), out("y", "Y", ValueType.FLOAT), out("z", "Z", ValueType.FLOAT)),
                        List.of()),
                (n, i, c) -> {
                    var v = i.get("v").code();
                    return Map.of(
                            "x", new Expr(v + ".x", ValueType.FLOAT),
                            "y", new Expr(v + ".y", ValueType.FLOAT),
                            "z", new Expr(v + ".z", ValueType.FLOAT));
                });
        register(metadata, codegen,
                type("split.vec4", "split", "Split Vec4",
                        List.of(in("v", "V", ValueType.VEC4),
                                out("x", "X", ValueType.FLOAT), out("y", "Y", ValueType.FLOAT),
                                out("z", "Z", ValueType.FLOAT), out("w", "W", ValueType.FLOAT)),
                        List.of()),
                (n, i, c) -> {
                    var v = i.get("v").code();
                    return Map.of(
                            "x", new Expr(v + ".x", ValueType.FLOAT),
                            "y", new Expr(v + ".y", ValueType.FLOAT),
                            "z", new Expr(v + ".z", ValueType.FLOAT),
                            "w", new Expr(v + ".w", ValueType.FLOAT));
                });

        metadata.register(type(GlslGenerator.OUTPUT_TYPE_ID, "output", "Fragment Color",
                List.of(in(GlslGenerator.OUTPUT_PORT, "Color", ValueType.COLOR)), List.of()));
    }

    // ---- 注册辅助 ----

    private static void register(NodeRegistry metadata, GlslNodeRegistry codegen, NodeType type, GlslNodeGenerator gen) {
        metadata.register(type);
        codegen.register(type.id(), gen);
    }

    private static void registerParam(NodeRegistry metadata, GlslNodeRegistry codegen, String id, ValueType type) {
        register(metadata, codegen,
                type(id, "input", "Parameter",
                        List.of(out("out", "Out", type)),
                        List.of(prop("param", "Parameter", ValueType.STRING, Value.string("")))),
                (n, i, c) -> Map.of("out", new Expr(c.parameterUniform(n.properties().get("param")), type)));
    }

    private static void registerBinary(NodeRegistry metadata, GlslNodeRegistry codegen, String id, String name, String op) {
        register(metadata, codegen,
                type(id, "math", name,
                        List.of(in("a", "A", ValueType.FLOAT), in("b", "B", ValueType.FLOAT), out("out", "Out", ValueType.FLOAT)),
                        List.of()),
                (n, i, c) -> Map.of("out", new Expr("(" + i.get("a").code() + " " + op + " " + i.get("b").code() + ")", ValueType.FLOAT)));
    }

    private static void registerVec3Binary(NodeRegistry metadata, GlslNodeRegistry codegen, String id, String name, String op) {
        register(metadata, codegen,
                type(id, "math", name,
                        List.of(in("a", "A", ValueType.VEC3), in("b", "B", ValueType.VEC3), out("out", "Out", ValueType.VEC3)),
                        List.of()),
                (n, i, c) -> Map.of("out", new Expr("(" + i.get("a").code() + " " + op + " " + i.get("b").code() + ")", ValueType.VEC3)));
    }

    /** 一元浮点函数：`fn(x)`；fn 为 null 时用自定义模板（reciprocal/saturate/smoothstep 等）。 */
    private static void registerUnaryFloat(NodeRegistry metadata, GlslNodeRegistry codegen, String id, String name, String fn) {
        var body = switch (id) {
            case "math.reciprocal" -> "(1.0 / {x})";
            case "math.saturate" -> "clamp({x}, 0.0, 1.0)";
            case "math.smoothstep" -> "smoothstep(0.0, 1.0, clamp({x}, 0.0, 1.0))";
            default -> fn == null ? null : fn + "({x})";
        };
        final String template = body;
        register(metadata, codegen,
                type(id, "math", name,
                        List.of(in("x", "X", ValueType.FLOAT), out("out", "Out", ValueType.FLOAT)), List.of()),
                (n, i, c) -> Map.of("out", new Expr(template.replace("{x}", i.get("x").code()), ValueType.FLOAT)));
    }

    private static void registerBinaryFloat(NodeRegistry metadata, GlslNodeRegistry codegen, String id, String name, String fn) {
        register(metadata, codegen,
                type(id, "math", name,
                        List.of(in("a", "A", ValueType.FLOAT), in("b", "B", ValueType.FLOAT), out("out", "Out", ValueType.FLOAT)),
                        List.of()),
                (n, i, c) -> Map.of("out", new Expr(fn + "(" + i.get("a").code() + ", " + i.get("b").code() + ")", ValueType.FLOAT)));
    }

    /** 解析渐变 stops（"t:r,g,b,a;t:r,g,b,a;..."）为 GLSL mix 链。 */
    private static Expr gradientExpr(GraphNode node, String t) {
        String stops = node.properties().getOrDefault("stops", "0.0:0,0,0,1;1.0:1,1,1,1");
        List<float[]> parsed = parseStops(stops);
        if (parsed.isEmpty()) {
            return new Expr("vec4(1.0)", ValueType.COLOR);
        }
        String expr = colorLiteral(parsed.get(0));
        for (int i = 1; i < parsed.size(); i++) {
            float[] prev = parsed.get(i - 1);
            float[] cur = parsed.get(i);
            float span = cur[0] - prev[0];
            String seg = span <= 0f ? "0.0" : "(clamp((" + t + " - " + prev[0] + ") / " + span + ", 0.0, 1.0))";
            expr = "mix(" + expr + ", " + colorLiteral(cur) + ", " + seg + ")";
        }
        return new Expr(expr, ValueType.COLOR);
    }

    private static List<float[]> parseStops(String stops) {
        var list = new java.util.ArrayList<float[]>();
        for (var stop : stops.split(";")) {
            var parts = stop.split(":");
            if (parts.length != 2) continue;
            var t = Float.parseFloat(parts[0].trim());
            var rgba = parts[1].split(",");
            if (rgba.length < 3) continue;
            float r = Float.parseFloat(rgba[0].trim());
            float g = Float.parseFloat(rgba[1].trim());
            float b = Float.parseFloat(rgba[2].trim());
            float a = rgba.length > 3 ? Float.parseFloat(rgba[3].trim()) : 1f;
            list.add(new float[]{t, r, g, b, a});
        }
        return list;
    }

    private static String colorLiteral(float[] rgba) {
        return "vec4(" + rgba[1] + ", " + rgba[2] + ", " + rgba[3] + ", " + rgba[4] + ")";
    }

    private static NodeType type(String id, String category, String name, List<PortSpec> ports, List<PropertySpec> props) {
        return new NodeType(id, category, name, ports, props);
    }

    private static PortSpec in(String id, String name, ValueType t) {
        return new PortSpec(id, name, PortDirection.INPUT, t, defaultValue(t));
    }

    private static PortSpec in(String id, String name, ValueType t, Value def) {
        return new PortSpec(id, name, PortDirection.INPUT, t, def);
    }

    private static PortSpec out(String id, String name, ValueType t) {
        return new PortSpec(id, name, PortDirection.OUTPUT, t, defaultValue(t));
    }

    private static PropertySpec prop(String id, String name, ValueType t, Value def) {
        return new PropertySpec(id, name, t, def, Optional.empty());
    }

    private static Value defaultValue(ValueType t) {
        return switch (t) {
            case FLOAT -> Value.of(0f);
            case VEC2 -> Value.of(new Vector2f());
            case VEC3 -> Value.of(new Vector3f());
            case VEC4 -> Value.of(new Vector4f());
            case COLOR -> Value.color(1f, 1f, 1f, 1f);
            case INT -> Value.of(0);
            case BOOL -> Value.of(false);
            case SAMPLER -> Value.sampler("");
            case TIME -> Value.of(0f);
            case STRING -> Value.string("");
            default -> Value.of(0f);
        };
    }

    private static float parseFloat(GraphNode node, String prop) {
        return Float.parseFloat(node.properties().getOrDefault(prop, "0.0"));
    }

    private static Expr colorLiteral(String csv) {
        var parts = csv.split(",");
        float r = parts.length > 0 ? Float.parseFloat(parts[0].trim()) : 1f;
        float g = parts.length > 1 ? Float.parseFloat(parts[1].trim()) : 1f;
        float b = parts.length > 2 ? Float.parseFloat(parts[2].trim()) : 1f;
        float a = parts.length > 3 ? Float.parseFloat(parts[3].trim()) : 1f;
        return GlslLiterals.of(Value.color(r, g, b, a));
    }
}
