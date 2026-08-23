package org.academy.api.client.render.graph.type;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class CurveSamplerTest {

    @Test
    void linearInterpolation() {
        var curve = new Curve(List.of(
                new Curve.Keyframe(0f, 0f),
                new Curve.Keyframe(1f, 10f)
        ));
        assertEquals(0f, CurveSampler.sample(curve, 0f));
        assertEquals(5f, CurveSampler.sample(curve, 0.5f), 0.001f);
        assertEquals(10f, CurveSampler.sample(curve, 1f));
    }

    @Test
    void clampsOutsideRange() {
        var curve = new Curve(List.of(
                new Curve.Keyframe(0.2f, 2f),
                new Curve.Keyframe(0.8f, 8f)
        ));
        assertEquals(2f, CurveSampler.sample(curve, 0f));
        assertEquals(8f, CurveSampler.sample(curve, 1f));
    }

    @Test
    void stepHoldsPreviousValue() {
        var curve = new Curve(List.of(
                new Curve.Keyframe(0f, 1f),
                Curve.Keyframe.step(0.5f, 9f),
                new Curve.Keyframe(1f, 9f)
        ));
        assertEquals(1f, CurveSampler.sample(curve, 0.49f));
        assertEquals(9f, CurveSampler.sample(curve, 0.5f));
    }

    @Test
    void smoothUsesSmoothstep() {
        var curve = new Curve(List.of(
                new Curve.Keyframe(0f, 0f),
                Curve.Keyframe.smooth(1f, 1f)
        ));
        assertEquals(0f, CurveSampler.sample(curve, 0f));
        assertEquals(0.5f, CurveSampler.sample(curve, 0.5f), 0.001f);
        assertEquals(1f, CurveSampler.sample(curve, 1f));
    }

    @Test
    void bezierUsesTangents() {
        // 切线为 0 时 bezier ≈ 线性
        var flat = new Curve(List.of(
                new Curve.Keyframe(0f, 0f, 0f, 0f, Curve.Interpolation.BEZIER),
                new Curve.Keyframe(1f, 10f, 0f, 0f, Curve.Interpolation.BEZIER)
        ));
        assertEquals(5f, CurveSampler.sample(flat, 0.5f), 0.001f);

        // 陡切线使中点值高于线性（前段上翘）
        var steep = new Curve(List.of(
                new Curve.Keyframe(0f, 0f, 0f, 5f, Curve.Interpolation.BEZIER),
                new Curve.Keyframe(1f, 10f, 5f, 0f, Curve.Interpolation.BEZIER)
        ));
        assertEquals(10f, CurveSampler.sample(steep, 1f), 0.001f);
    }

    @Test
    void emptyAndSingleKeyframe() {
        assertEquals(0f, CurveSampler.sample(new Curve(List.of()), 0.5f));
        assertEquals(3f, CurveSampler.sample(new Curve(List.of(new Curve.Keyframe(0.5f, 3f))), 0.1f));
    }
}
