package org.academy.api.client.render.shader.pipeline;

import com.mojang.blaze3d.buffers.Std140Builder;
import org.academy.api.client.render.graph.model.GraphParameter;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行时材质：持有图参数值（默认值初始化，可逐项覆盖），可写入 std140 uniform 块。
 */
public final class GraphMaterial {
    private final UniformLayout layout;
    private final List<GraphParameter> parameters;
    private final Map<String, Value> values = new LinkedHashMap<>();

    public GraphMaterial(UniformLayout layout, List<GraphParameter> parameters) {
        this.layout = layout;
        this.parameters = List.copyOf(parameters);
        for (var p : parameters) {
            values.put(p.id(), p.defaultValue());
        }
    }

    public void set(String parameterId, Value value) {
        if (!values.containsKey(parameterId)) {
            throw new IllegalArgumentException("unknown parameter: " + parameterId);
        }
        values.put(parameterId, value);
    }

    public Value get(String parameterId) {
        var value = values.get(parameterId);
        if (value == null) {
            throw new IllegalArgumentException("unknown parameter: " + parameterId);
        }
        return value;
    }

    public Map<String, Value> values() {
        return Map.copyOf(values);
    }

    public UniformLayout layout() {
        return layout;
    }

    /**
     * 图所需的纹理绑定槽位（来自布局，含 texture.sample 属性与 SAMPLER 参数）。
     */
    public List<SamplerBinding> samplerBindings() {
        return layout.samplers();
    }

    /**
     * 按 {@link UniformLayout} 顺序写入：固定首成员 {@code Time}，随后各参数。
     * std140 对齐由 {@link Std140Builder} 自动处理，与 {@link UniformLayout} 规则一致。
     */
    public void write(Std140Builder builder, float time) {
        builder.putFloat(time);
        for (var parameter : parameters) {
            if (!isUniformType(parameter.type())) continue; // sampler/curve/gradient 不走 UBO
            writeValue(builder, values.get(parameter.id()));
        }
    }

    private static boolean isUniformType(ValueType type) {
        return switch (type) {
            case FLOAT, INT, VEC2, VEC3, VEC4, COLOR -> true;
            default -> false;
        };
    }

    private static void writeValue(Std140Builder builder, Value value) {
        switch (value.type()) {
            case FLOAT -> builder.putFloat(value.asFloat());
            case INT -> builder.putInt(value.asInt());
            case VEC2 -> {
                var v = value.asVec2();
                builder.putVec2(v.x, v.y);
            }
            case VEC3 -> {
                var v = value.asVec3();
                builder.putVec3(v.x, v.y, v.z);
            }
            case VEC4, COLOR -> {
                var v = value.asVec4();
                builder.putVec4(v.x, v.y, v.z, v.w);
            }
            default -> throw new IllegalArgumentException("not a uniform value type: " + value.type());
        }
    }
}
