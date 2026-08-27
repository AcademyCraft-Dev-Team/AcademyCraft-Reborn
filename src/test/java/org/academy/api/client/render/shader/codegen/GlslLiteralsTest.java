package org.academy.api.client.render.shader.codegen;

import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GlslLiteralsTest {
    @Test
    void scalarLiterals() {
        assertEquals(new Expr("1.0", ValueType.FLOAT), GlslLiterals.of(Value.of(1f)));
        assertEquals(new Expr("2", ValueType.INT), GlslLiterals.of(Value.of(2)));
        assertEquals(new Expr("true", ValueType.BOOL), GlslLiterals.of(Value.of(true)));
    }

    @Test
    void floatLiteralUsesIntegerNotationForWholeNumbers() {
        assertEquals("3.0", GlslLiterals.of(Value.of(3f)).code());
        assertEquals("0.5", GlslLiterals.of(Value.of(0.5f)).code());
    }

    @Test
    void vectorLiterals() {
        assertEquals("vec2(1.0, 2.0)", GlslLiterals.of(Value.of(new Vector2f(1f, 2f))).code());
        assertEquals("vec3(1.0, 2.0, 3.0)", GlslLiterals.of(Value.of(new Vector3f(1f, 2f, 3f))).code());
        assertEquals("vec4(1.0, 2.0, 3.0, 4.0)",
                GlslLiterals.of(Value.color(new Vector4f(1f, 2f, 3f, 4f))).code());
    }

    @Test
    void unsupportedTypeThrows() {
        assertThrows(IllegalArgumentException.class, () -> GlslLiterals.of(Value.sampler("x")));
    }
}
