package org.academy.api.client.render.vfxgraph.operator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.academy.api.client.render.graph.model.PortDirection;
import org.academy.api.client.render.graph.registry.NodeRegistry;
import org.academy.api.client.render.graph.registry.NodeType;
import org.academy.api.client.render.graph.registry.PortSpec;
import org.academy.api.client.render.graph.registry.PropertySpec;
import org.academy.api.client.render.graph.type.CurveSampler;
import org.academy.api.client.render.graph.type.GradientSampler;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;
import org.academy.api.client.render.vfxgraph.model.ParticleAttribute;
import org.academy.api.client.render.vfxgraph.model.VfxOperatorNode;
import org.academy.api.client.render.vfxgraph.sim.ParticleBuffer;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * VFX 算子目录（M25）：注册算子元数据（核心 NodeRegistry，含端口）与算子工厂（VfxOperatorRegistry）。
 *
 * <p><b>数据流</b>：算子输出端口经 {@code VfxDataEdge} 驱动块/算子的输入端口。求值分两类：
 * 不依赖粒子的算子（constant/param/math 纯输入）可在编译期折叠；attr-read 算子逐粒子求值
 * （从 {@link ParticleBuffer} 读取属性）。曲线/渐变算子经 {@code SimContext} 黑板参数采样。</p>
 */
public final class VfxOperators {
    private VfxOperators() {
    }

    public static void registerAll(NodeRegistry metadata, VfxOperatorRegistry ops) {
        // ==================== attr-read（逐粒子） ====================

        for (var attribute : ParticleAttribute.values()) {
            var typeId = "vfx.op.attr_" + attribute.name().toLowerCase(java.util.Locale.ROOT);
            var type = new NodeType(typeId, "attribute", "Attribute " + attribute.name(),
                    List.of(out("out", "Out", attribute.valueType(), defaultOf(attribute))),
                    List.of());
            metadata.register(type);
            ops.register(typeId, (node, inputs) -> ctx -> {
                if (ctx.particleIndex() < 0 || ctx.buffer() == null) return defaultOf(attribute);
                return readAttribute(ctx.buffer(), ctx.particleIndex(), attribute);
            });
        }

        // ==================== constant ====================

        metadata.register(new NodeType("vfx.op.constant", "math", "Constant",
                List.of(out("out", "Out", ValueType.FLOAT, Value.of(0f))),
                List.of(prop("value", "Value", ValueType.FLOAT, Value.of(0f)))));
        ops.register("vfx.op.constant", (node, inputs) -> ctx -> {
            float value = propFloat(node, "value", 0f);
            // 常量：编译期可折叠，但保持统一求值路径
            return Value.of(value);
        });

        // ==================== param（黑板/存活参数） ====================

        metadata.register(new NodeType("vfx.op.param_float", "param", "Float Parameter",
                List.of(out("out", "Out", ValueType.FLOAT, Value.of(0f))),
                List.of(prop("param", "Parameter", ValueType.STRING, Value.string("")),
                        prop("value", "Value", ValueType.FLOAT, Value.of(0f)))));
        ops.register("vfx.op.param_float", (node, inputs) -> ctx -> {
            String param = propString(node, "param", "");
            float fallback = propFloat(node, "value", 0f);
            return Value.of(ctx.simContext() == null ? fallback : ctx.simContext().paramFloat(param, fallback));
        });

        metadata.register(new NodeType("vfx.op.param_vec3", "param", "Vec3 Parameter",
                List.of(out("out", "Out", ValueType.VEC3, Value.of(new Vector3f()))),
                List.of(prop("param", "Parameter", ValueType.STRING, Value.string("")),
                        prop("x", "X", ValueType.FLOAT, Value.of(0f)),
                        prop("y", "Y", ValueType.FLOAT, Value.of(0f)),
                        prop("z", "Z", ValueType.FLOAT, Value.of(0f)))));
        ops.register("vfx.op.param_vec3", (node, inputs) -> ctx -> {
            String param = propString(node, "param", "");
            float fx = propFloat(node, "x", 0f);
            float fy = propFloat(node, "y", 0f);
            float fz = propFloat(node, "z", 0f);
            if (ctx.simContext() == null) return Value.of(new Vector3f(fx, fy, fz));
            return Value.of(new Vector3f(
                    ctx.simContext().paramVec3(param, 0, fx),
                    ctx.simContext().paramVec3(param, 1, fy),
                    ctx.simContext().paramVec3(param, 2, fz)));
        });

        metadata.register(new NodeType("vfx.op.param_color", "param", "Color Parameter",
                List.of(out("out", "Out", ValueType.COLOR, Value.color(1f, 1f, 1f, 1f))),
                List.of(prop("param", "Parameter", ValueType.STRING, Value.string("")),
                        prop("r", "R", ValueType.FLOAT, Value.of(1f)),
                        prop("g", "G", ValueType.FLOAT, Value.of(1f)),
                        prop("b", "B", ValueType.FLOAT, Value.of(1f)),
                        prop("a", "A", ValueType.FLOAT, Value.of(1f)))));
        ops.register("vfx.op.param_color", (node, inputs) -> ctx -> {
            String param = propString(node, "param", "");
            float fr = propFloat(node, "r", 1f);
            float fg = propFloat(node, "g", 1f);
            float fb = propFloat(node, "b", 1f);
            float fa = propFloat(node, "a", 1f);
            if (ctx.simContext() == null) return Value.color(fr, fg, fb, fa);
            return Value.color(
                    ctx.simContext().paramColor(param, 0, fr),
                    ctx.simContext().paramColor(param, 1, fg),
                    ctx.simContext().paramColor(param, 2, fb),
                    ctx.simContext().paramColor(param, 3, fa));
        });

        // ==================== math（二元 FLOAT） ====================

        registerBinary(metadata, ops, "vfx.op.add", "Add", (a, b) -> a + b);
        registerBinary(metadata, ops, "vfx.op.sub", "Subtract", (a, b) -> a - b);
        registerBinary(metadata, ops, "vfx.op.mul", "Multiply", (a, b) -> a * b);
        registerBinary(metadata, ops, "vfx.op.div", "Divide", (a, b) -> b == 0f ? 0f : a / b);

        // ==================== curve / gradient ====================

        metadata.register(new NodeType("vfx.op.curve", "curve", "Sample Curve",
                List.of(in("t", "T", ValueType.FLOAT, Value.of(0f)),
                        out("out", "Out", ValueType.FLOAT, Value.of(0f))),
                List.of(prop("curve", "Curve", ValueType.STRING, Value.string("")))));
        ops.register("vfx.op.curve", (node, inputs) -> {
            String curveId = propString(node, "curve", "");
            var tIn = inputs.get("t");
            return ctx -> {
                float t = tIn != null ? asFloat(tIn.eval(ctx), 0f)
                        : ctx.simContext() != null ? ctx.simContext().time() : 0f;
                var curve = ctx.simContext() != null ? ctx.simContext().curve(curveId) : null;
                if (curve == null) return Value.of(0f);
                return Value.of(CurveSampler.sample(curve, t));
            };
        });

        metadata.register(new NodeType("vfx.op.gradient", "curve", "Sample Gradient",
                List.of(in("t", "T", ValueType.FLOAT, Value.of(0f)),
                        out("out", "Out", ValueType.COLOR, Value.color(1f, 1f, 1f, 1f))),
                List.of(prop("gradient", "Gradient", ValueType.STRING, Value.string("")))));
        ops.register("vfx.op.gradient", (node, inputs) -> {
            String gradientId = propString(node, "gradient", "");
            var tIn = inputs.get("t");
            return ctx -> {
                float t = tIn != null ? asFloat(tIn.eval(ctx), 0f)
                        : ctx.simContext() != null ? ctx.simContext().time() : 0f;
                var gradient = ctx.simContext() != null ? ctx.simContext().gradient(gradientId) : null;
                if (gradient == null) return Value.color(1f, 1f, 1f, 1f);
                var c = GradientSampler.sample(gradient, t);
                return Value.color(c.x, c.y, c.z, c.w);
            };
        });

        // ==================== param curve/gradient（复制黑板源到引用参数 id，供 over-life 按 param 引用采样） ====================

        metadata.register(new NodeType("vfx.op.param_curve", "param", "Curve Parameter",
                List.of(out("out", "Out", ValueType.CURVE, Value.curve(new org.academy.api.client.render.graph.type.Curve(
                        List.of(new org.academy.api.client.render.graph.type.Curve.Keyframe(0f, 0f, 0f, 0f,
                                org.academy.api.client.render.graph.type.Curve.Interpolation.LINEAR)))))),
                List.of(prop("param", "Parameter", ValueType.STRING, Value.string("")),
                        prop("curve", "Source Curve", ValueType.STRING, Value.string("")))));
        ops.register("vfx.op.param_curve", (node, inputs) -> ctx -> {
            String param = propString(node, "param", "");
            var empty = Value.curve(new org.academy.api.client.render.graph.type.Curve(List.of()));
            if (param.isEmpty() || ctx.simContext() == null) return empty;
            var existing = ctx.simContext().curve(param);
            if (existing != null) return Value.curve(existing);
            var source = ctx.simContext().curve(propString(node, "curve", ""));
            if (source != null) {
                ctx.simContext().curveIfAbsent(param, source);
                return Value.curve(source);
            }
            return empty;
        });

        metadata.register(new NodeType("vfx.op.param_gradient", "param", "Gradient Parameter",
                List.of(out("out", "Out", ValueType.GRADIENT, Value.gradient(new org.academy.api.client.render.graph.type.Gradient(
                        List.of(new org.academy.api.client.render.graph.type.Gradient.ColorStop(0f, 1f, 1f, 1f, 1f)))))),
                List.of(prop("param", "Parameter", ValueType.STRING, Value.string("")),
                        prop("gradient", "Source Gradient", ValueType.STRING, Value.string("")))));
        ops.register("vfx.op.param_gradient", (node, inputs) -> ctx -> {
            String param = propString(node, "param", "");
            if (param.isEmpty() || ctx.simContext() == null) return Value.gradient(new org.academy.api.client.render.graph.type.Gradient(
                    List.of(new org.academy.api.client.render.graph.type.Gradient.ColorStop(0f, 1f, 1f, 1f, 1f))));
            if (ctx.simContext().gradient(param) != null) {
                return Value.gradient(ctx.simContext().gradient(param));
            }
            var source = ctx.simContext().gradient(propString(node, "gradient", ""));
            if (source != null) {
                ctx.simContext().gradientIfAbsent(param, source);
                return Value.gradient(source);
            }
            return Value.gradient(new org.academy.api.client.render.graph.type.Gradient(
                    List.of(new org.academy.api.client.render.graph.type.Gradient.ColorStop(0f, 1f, 1f, 1f, 1f))));
        });
    }

    private static void registerBinary(NodeRegistry metadata, VfxOperatorRegistry ops,
                                       String typeId, String name, BinaryFloat fn) {
        metadata.register(new NodeType(typeId, "math", name,
                List.of(
                        in("a", "A", ValueType.FLOAT, Value.of(0f)),
                        in("b", "B", ValueType.FLOAT, Value.of(0f)),
                        out("out", "Out", ValueType.FLOAT, Value.of(0f))),
                List.of()));
        ops.register(typeId, (node, inputs) -> {
            var a = inputs.get("a");
            var b = inputs.get("b");
            return ctx -> Value.of(fn.apply(
                    a != null ? asFloat(a.eval(ctx), 0f) : propFloat(node, "a", 0f),
                    b != null ? asFloat(b.eval(ctx), 0f) : propFloat(node, "b", 0f)));
        });
    }

    private interface BinaryFloat {
        float apply(float a, float b);
    }

    // ==================== 辅助 ====================

    /** attr-read 默认值：非粒子上下文/缓冲空时返回（匹配属性类型）。 */
    private static Value defaultOf(ParticleAttribute attribute) {
        return switch (attribute) {
            case POSITION, VELOCITY -> Value.of(new Vector3f());
            case COLOR -> Value.color(1f, 1f, 1f, 1f);
            case LAYER -> Value.of(0);
            default -> Value.of(0f);
        };
    }

    private static Value readAttribute(ParticleBuffer buffer, int i, ParticleAttribute attribute) {
        return switch (attribute) {
            case POSITION -> Value.of(new Vector3f(buffer.positionX(i), buffer.positionY(i), buffer.positionZ(i)));
            case VELOCITY -> Value.of(new Vector3f(buffer.velocityX(i), buffer.velocityY(i), buffer.velocityZ(i)));
            case SIZE -> Value.of(buffer.size(i));
            case COLOR -> Value.color(buffer.colorR(i), buffer.colorG(i), buffer.colorB(i), buffer.alpha(i));
            case ALPHA -> Value.of(buffer.alpha(i));
            case AGE -> Value.of(buffer.age(i));
            case LIFETIME -> Value.of(buffer.lifetime(i));
            case ROTATION -> Value.of(buffer.rotation(i));
            case MASS -> Value.of(buffer.mass(i));
            case SEED -> Value.of(buffer.seed(i));
            case LAYER -> Value.of((int) buffer.layer(i));
        };
    }

    private static float asFloat(Value value, float fallback) {
        return value.type() == ValueType.FLOAT ? value.asFloat() : fallback;
    }

    private static float propFloat(VfxOperatorNode node, String id, float def) {
        var v = node.properties().get(id);
        return v == null ? def : Float.parseFloat(v);
    }

    private static String propString(VfxOperatorNode node, String id, String def) {
        return node.properties().getOrDefault(id, def);
    }

    private static PortSpec in(String id, String name, ValueType type, Value def) {
        return new PortSpec(id, name, PortDirection.INPUT, type, def);
    }

    private static PortSpec out(String id, String name, ValueType type, Value def) {
        return new PortSpec(id, name, PortDirection.OUTPUT, type, def);
    }

    private static PropertySpec prop(String id, String name, ValueType type, Value def) {
        return new PropertySpec(id, name, type, def, Optional.empty());
    }
}
