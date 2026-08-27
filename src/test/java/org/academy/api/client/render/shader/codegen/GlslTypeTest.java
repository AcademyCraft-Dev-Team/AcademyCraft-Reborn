package org.academy.api.client.render.shader.codegen;

import org.academy.api.client.render.graph.type.ValueType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GlslTypeTest {
    @Test
    void mapsTypesToGlsl() {
        assertEquals("float", GlslType.of(ValueType.FLOAT));
        assertEquals("vec3", GlslType.of(ValueType.VEC3));
        assertEquals("vec4", GlslType.of(ValueType.COLOR));
        assertEquals("sampler2D", GlslType.of(ValueType.SAMPLER));
    }

    @Test
    void floatBroadcastsToVec3() {
        var e = GlslType.convert(new Expr("x", ValueType.FLOAT), ValueType.VEC3);
        assertEquals("vec3(x)", e.code());
        assertEquals(ValueType.VEC3, e.type());
    }

    @Test
    void vec3AppendsOneToVec4() {
        var e = GlslType.convert(new Expr("v", ValueType.VEC3), ValueType.VEC4);
        assertEquals("vec4(v, 1.0)", e.code());
    }

    @Test
    void vec4DowncastsToVec2BySwizzle() {
        var e = GlslType.convert(new Expr("v", ValueType.VEC4), ValueType.VEC2);
        assertEquals("v.xy", e.code());
    }

    @Test
    void identityConversionReturnsSameInstance() {
        var e = new Expr("x", ValueType.FLOAT);
        assertEquals(e, GlslType.convert(e, ValueType.FLOAT));
    }

    @Test
    void vectorToFloatThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> GlslType.convert(new Expr("v", ValueType.VEC3), ValueType.FLOAT));
    }
}
