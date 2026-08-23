package org.academy.api.client.render.shader.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mojang.blaze3d.buffers.Std140Builder;
import java.util.List;
import java.util.Optional;
import org.academy.api.client.render.graph.model.GraphParameter;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryStack;

class GraphMaterialTest {
    private static GraphParameter param(String id, ValueType type, Value def) {
        return new GraphParameter(id, id, type, def, Optional.empty());
    }

    @Test
    void defaultsFromParameters() {
        var layout = new UniformLayout(List.of(
                param("a", ValueType.FLOAT, Value.of(1f)),
                param("b", ValueType.FLOAT, Value.of(2f))
        ));
        var material = new GraphMaterial(layout, List.of(
                param("a", ValueType.FLOAT, Value.of(1f)),
                param("b", ValueType.FLOAT, Value.of(2f))
        ));
        assertEquals(1f, material.get("a").asFloat(), 1e-5f);
        assertEquals(2f, material.get("b").asFloat(), 1e-5f);
    }

    @Test
    void setOverridesValue() {
        var layout = new UniformLayout(List.of(param("a", ValueType.FLOAT, Value.of(1f))));
        var material = new GraphMaterial(layout, List.of(param("a", ValueType.FLOAT, Value.of(1f))));
        material.set("a", Value.of(9f));
        assertEquals(9f, material.get("a").asFloat(), 1e-5f);
    }

    @Test
    void unknownParameterThrows() {
        var layout = new UniformLayout(List.of());
        var material = new GraphMaterial(layout, List.of());
        assertThrows(IllegalArgumentException.class, () -> material.set("nope", Value.of(1f)));
        assertThrows(IllegalArgumentException.class, () -> material.get("nope"));
    }

    @Test
    void writeEmitsTimeThenUniformValues() {
        var layout = new UniformLayout(List.of(
                param("a", ValueType.FLOAT, Value.of(1f)),
                param("v", ValueType.VEC3, Value.of(new org.joml.Vector3f(1f, 2f, 3f)))
        ));
        var material = new GraphMaterial(layout, List.of(
                param("a", ValueType.FLOAT, Value.of(1f)),
                param("v", ValueType.VEC3, Value.of(new org.joml.Vector3f(1f, 2f, 3f)))
        ));
        try (var stack = MemoryStack.stackPush()) {
            var builder = Std140Builder.onStack(stack, layout.totalSize());
            material.write(builder, 0.5f);
            var buf = builder.get();
            // Time(4) + u_a(4) → u_v 对齐到 16（VEC3 align=16）
            assertEquals(0.5f, buf.getFloat(0), 1e-5f);
            assertEquals(1f, buf.getFloat(16), 1e-5f);
            assertEquals(2f, buf.getFloat(20), 1e-5f);
            assertEquals(3f, buf.getFloat(24), 1e-5f);
        }
    }

    @Test
    void samplerAndCurveParametersSkippedInWrite() {
        var layout = new UniformLayout(List.of(
                param("s", ValueType.SAMPLER, Value.sampler("x")),
                param("c", ValueType.CURVE, Value.curve(new org.academy.api.client.render.graph.type.Curve(
                        List.of()))),
                param("f", ValueType.FLOAT, Value.of(7f))
        ));
        var material = new GraphMaterial(layout, List.of(
                param("s", ValueType.SAMPLER, Value.sampler("x")),
                param("c", ValueType.CURVE, Value.curve(new org.academy.api.client.render.graph.type.Curve(
                        List.of()))),
                param("f", ValueType.FLOAT, Value.of(7f))
        ));
        try (var stack = MemoryStack.stackPush()) {
            var builder = Std140Builder.onStack(stack, layout.totalSize());
            material.write(builder, 1f);
            var buf = builder.get();
            assertEquals(1f, buf.getFloat(0), 1e-5f);
            assertEquals(7f, buf.getFloat(4), 1e-5f);
        }
    }

    @Test
    void samplerBindingsExposedFromLayout() {
        var layout = new UniformLayout(List.of(
                param("s", ValueType.SAMPLER, Value.sampler("minecraft:textures/block/stone.png"))
        ));
        var material = new GraphMaterial(layout, List.of(
                param("s", ValueType.SAMPLER, Value.sampler("minecraft:textures/block/stone.png"))
        ));
        var bindings = material.samplerBindings();
        assertEquals(1, bindings.size());
        assertEquals("Sampler0", bindings.get(0).uniformName());
        assertEquals("minecraft:textures/block/stone.png", bindings.get(0).identifier());
    }
}
