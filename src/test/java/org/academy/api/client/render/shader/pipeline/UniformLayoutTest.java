package org.academy.api.client.render.shader.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import org.academy.api.client.render.graph.model.GraphParameter;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;
import org.junit.jupiter.api.Test;

class UniformLayoutTest {
    @Test
    void timeMemberComesFirst() {
        var layout = new UniformLayout(List.of());
        var entries = layout.entries();
        assertEquals(1, entries.size());
        assertEquals("Time", entries.get(0).name());
        assertEquals(0, entries.get(0).offset());
    }

    @Test
    void scalarParametersAreTightlyPacked() {
        var layout = new UniformLayout(List.of(
                param("a", ValueType.FLOAT),
                param("b", ValueType.FLOAT)
        ));

        var entries = layout.entries();
        assertEquals("u_a", entries.get(1).name());
        assertEquals(4, entries.get(1).offset());
        assertEquals(8, entries.get(2).offset());
        assertEquals(16, layout.totalSize());
    }

    @Test
    void vec3IsAlignedTo16() {
        var layout = new UniformLayout(List.of(
                param("f", ValueType.FLOAT),
                param("v", ValueType.VEC3)
        ));

        var entries = layout.entries();
        assertEquals(16, entries.get(2).offset());
        assertEquals(12, entries.get(2).size());
    }

    @Test
    void samplerParametersAreSkipped() {
        var layout = new UniformLayout(List.of(
                param("s", ValueType.SAMPLER),
                param("f", ValueType.FLOAT)
        ));
        var entries = layout.entries();
        // sampler 参数走纹理绑定，不进 std140
        assertEquals(2, entries.size());
        assertEquals("u_f", entries.get(1).name());
        assertEquals(4, entries.get(1).offset());
    }

    @Test
    void samplerParametersBecomeBindings() {
        var layout = new UniformLayout(List.of(
                new GraphParameter("s", "S", ValueType.SAMPLER, Value.sampler("minecraft:textures/block/stone.png"), Optional.empty()),
                new GraphParameter("f", "F", ValueType.FLOAT, Value.of(1f), Optional.empty())
        ));
        var samplers = layout.samplers();
        assertEquals(1, samplers.size());
        assertEquals("Sampler0", samplers.get(0).uniformName());
        assertEquals("minecraft:textures/block/stone.png", samplers.get(0).identifier());
    }

    @Test
    void explicitSamplerBindingsOverrideDerived() {
        var layout = new UniformLayout(
                List.of(param("f", ValueType.FLOAT)),
                List.of(new SamplerBinding("Sampler0", "a.png"), new SamplerBinding("Sampler1", "b.png"))
        );
        assertEquals(2, layout.samplers().size());
        assertEquals("Sampler1", layout.samplers().get(1).uniformName());
    }

    @Test
    void unsupportedTypeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new UniformLayout(List.of(param("s", ValueType.STRING))));
    }

    private static GraphParameter param(String id, ValueType type) {
        return new GraphParameter(id, id, type, Value.of(0f), Optional.empty());
    }
}
