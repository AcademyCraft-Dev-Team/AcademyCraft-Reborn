package org.academy.api.client.render.shader.pipeline;

import java.util.ArrayList;
import java.util.List;
import org.academy.api.client.render.graph.model.GraphParameter;
import org.academy.api.client.render.graph.type.ValueType;
import org.academy.api.client.render.shader.codegen.GlslGenerator;
import org.academy.api.client.render.shader.codegen.GlslNames;

/**
 * 图参数 std140 uniform 块布局。成员顺序：固定首成员 {@code Time}，随后各参数按声明序。
 * SAMPLER/CURVE/GRADIENT 不进 std140；SAMPLER 参数登记为 {@link #samplers()} 纹理绑定槽位。
 */
public final class UniformLayout {
    public record Entry(String name, ValueType type, int offset, int size) {
    }

    private final List<Entry> entries;
    private final List<SamplerBinding> samplers;
    private final int totalSize;

    public UniformLayout(List<GraphParameter> parameters) {
        this(parameters, deriveSamplerBindings(parameters));
    }

    public UniformLayout(List<GraphParameter> parameters, List<SamplerBinding> samplers) {
        var list = new ArrayList<Entry>();
        list.add(new Entry(GlslGenerator.TIME_MEMBER, ValueType.FLOAT, 0, 4));
        int offset = 4;
        for (var p : parameters) {
            if (p.type() == ValueType.SAMPLER || p.type() == ValueType.CURVE || p.type() == ValueType.GRADIENT) {
                continue; // 走纹理/内联函数，不进 std140
            }
            int align = alignOf(p.type());
            int size = sizeOf(p.type());
            offset = alignUp(offset, align);
            list.add(new Entry(GlslNames.uniformName(p.id()), p.type(), offset, size));
            offset += size;
        }
        this.entries = List.copyOf(list);
        this.samplers = List.copyOf(samplers);
        this.totalSize = alignUp(offset, 16);
    }

    private static List<SamplerBinding> deriveSamplerBindings(List<GraphParameter> parameters) {
        var bindings = new ArrayList<SamplerBinding>();
        var index = 0;
        for (var p : parameters) {
            if (p.type() == ValueType.SAMPLER) {
                var value = p.defaultValue();
                bindings.add(new SamplerBinding(SamplerBinding.uniformName(index++),
                        value.type() == ValueType.SAMPLER ? value.asSampler() : ""));
            }
        }
        return List.copyOf(bindings);
    }

    public List<Entry> entries() {
        return entries;
    }

    /** 图使用的 sampler 绑定槽位（uniform 名 → 纹理标识），与生成 GLSL 的 sampler 声明一致。 */
    public List<SamplerBinding> samplers() {
        return samplers;
    }

    public int totalSize() {
        return totalSize;
    }

    public static int alignOf(ValueType t) {
        return switch (t) {
            case FLOAT, INT, BOOL, TIME -> 4;
            case VEC2 -> 8;
            case VEC3, VEC4, COLOR -> 16;
            default -> throw new IllegalArgumentException("not a uniform type: " + t);
        };
    }

    public static int sizeOf(ValueType t) {
        return switch (t) {
            case FLOAT, INT, BOOL, TIME -> 4;
            case VEC2 -> 8;
            case VEC3 -> 12;
            case VEC4, COLOR -> 16;
            default -> throw new IllegalArgumentException("not a uniform type: " + t);
        };
    }

    private static int alignUp(int value, int align) {
        return (value + align - 1) / align * align;
    }
}
