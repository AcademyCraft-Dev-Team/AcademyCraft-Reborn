package org.academy.api.client.render.shader.codegen;

import org.academy.api.client.render.graph.type.Curve;
import org.academy.api.client.render.graph.type.Gradient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurveGradientGlslTest {

    @Test
    void curveFunctionEmitsSegments() {
        var curve = new Curve(List.of(
                new Curve.Keyframe(0f, 0f),
                Curve.Keyframe.smooth(1f, 1f)
        ));
        var source = CurveGradientGlsl.curveFunction(curve, "my_curve");
        assertTrue(source.contains("float _academy_curve_my_curve(float t)"));
        assertTrue(source.contains("smoothstep(0.0, 1.0"));
        assertTrue(source.contains("return 1.0;"));
    }

    @Test
    void gradientFunctionEmitsMixChain() {
        var gradient = new Gradient(List.of(
                new Gradient.ColorStop(0f, 0f, 0f, 0f, 1f),
                new Gradient.ColorStop(1f, 1f, 1f, 1f, 1f)
        ));
        var source = CurveGradientGlsl.gradientFunction(gradient, "g");
        assertTrue(source.contains("vec4 _academy_gradient_g(float t)"));
        assertTrue(source.contains("mix(vec4(0.0, 0.0, 0.0, 1.0), vec4(1.0, 1.0, 1.0, 1.0)"));
    }

    @Test
    void stepSegmentHoldsValue() {
        var curve = new Curve(List.of(
                new Curve.Keyframe(0f, 2f),
                Curve.Keyframe.step(1f, 9f)
        ));
        var source = CurveGradientGlsl.curveFunction(curve, "step");
        assertTrue(source.contains("if (t < 1.0) return 2.0;"));
    }

    @Test
    void namesAreSanitized() {
        assertEquals("_academy_curve_a_b", CurveGradientGlsl.curveName("a.b"));
        assertEquals("_academy_gradient_a_b", CurveGradientGlsl.gradientName("a-b"));
    }
}
