package org.academy.api.client.render.graph.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector2f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class TypeConversionsTest {
    private final TypeConverter converter = TypeConversions.INSTANCE;

    @Test
    void numericFamilyInterconverts() {
        assertTrue(converter.canConvert(ValueType.FLOAT, ValueType.VEC4));
        assertTrue(converter.canConvert(ValueType.VEC3, ValueType.FLOAT));
        assertTrue(converter.canConvert(ValueType.INT, ValueType.BOOL));
    }

    @Test
    void samplerDoesNotConvert() {
        assertFalse(converter.canConvert(ValueType.SAMPLER, ValueType.FLOAT));
        assertFalse(converter.canConvert(ValueType.FLOAT, ValueType.SAMPLER));
    }

    @Test
    void timeConvertsToFloatOnly() {
        assertTrue(converter.canConvert(ValueType.TIME, ValueType.FLOAT));
        assertFalse(converter.canConvert(ValueType.TIME, ValueType.VEC3));
    }

    @Test
    void floatBroadcastsToVec3() {
        var v = converter.convert(Value.of(2f), ValueType.VEC3);
        assertEquals(2f, v.asVec3().x);
        assertEquals(2f, v.asVec3().y);
        assertEquals(2f, v.asVec3().z);
    }

    @Test
    void vec2PadsToVec4() {
        var v = converter.convert(Value.of(new Vector2f(1f, 2f)), ValueType.VEC4);
        assertEquals(1f, v.asVec4().x);
        assertEquals(2f, v.asVec4().y);
        assertEquals(0f, v.asVec4().z);
        assertEquals(1f, v.asVec4().w);
    }

    @Test
    void colorConvertsToVec4() {
        var v = converter.convert(Value.color(0.5f, 0.6f, 0.7f, 1f), ValueType.VEC4);
        assertEquals(0.5f, v.asVec4().x);
        assertEquals(0.7f, v.asVec4().z);
    }

    @Test
    void unsupportedConversionThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> converter.convert(Value.sampler("a"), ValueType.FLOAT));
    }
}
