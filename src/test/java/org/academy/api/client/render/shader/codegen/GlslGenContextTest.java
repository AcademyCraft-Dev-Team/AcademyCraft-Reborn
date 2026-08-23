package org.academy.api.client.render.shader.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.academy.api.client.render.graph.type.Curve;
import org.academy.api.client.render.graph.type.Gradient;
import org.academy.api.client.render.graph.type.ValueType;
import org.junit.jupiter.api.Test;

class GlslGenContextTest {
    @Test
    void defaultCurveAndGradientAreNull() {
        var ctx = new GlslGenContext() {
            @Override
            public String parameterUniform(String parameterId) {
                return "u_" + parameterId;
            }

            @Override
            public void addHelper(String functionSource) {
            }
        };
        assertEquals("u_foo", ctx.parameterUniform("foo"));
        assertEquals(null, ctx.curve("foo"));
        assertEquals(null, ctx.gradient("foo"));
    }

    @Test
    void exprRecord() {
        var expr = new Expr("1.0", ValueType.FLOAT);
        assertEquals("1.0", expr.code());
        assertEquals(ValueType.FLOAT, expr.type());
    }

    @Test
    void glslProgramRecord() {
        var program = new GlslProgram("vs", "fs");
        assertEquals("vs", program.vertexSource());
        assertEquals("fs", program.fragmentSource());
    }

    @Test
    void curveAndGradientAreUsableData() {
        // 确认数据类可构造（黄金测试与采样器消费它们的通路）
        var curve = new Curve(java.util.List.of(new Curve.Keyframe(0f, 0f, 0f, 0f, Curve.Interpolation.LINEAR)));
        var gradient = new Gradient(java.util.List.of(new Gradient.ColorStop(0f, 1f, 0f, 0f, 1f)));
        assertEquals(1, curve.keyframes().size());
        assertEquals(1, gradient.stops().size());
    }
}
