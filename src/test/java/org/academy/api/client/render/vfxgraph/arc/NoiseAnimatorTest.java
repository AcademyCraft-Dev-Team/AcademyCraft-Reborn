package org.academy.api.client.render.vfxgraph.arc;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class NoiseAnimatorTest {

    @Test
    void animateMovesPoints() {
        var arc = new ArcCurve();
        // Generate a simple arc
        CurveGenerator.generate(arc, 0, 0, 0, 0, 1, 0, 0.01f, 12,
                0.8f, 0.2f, 0.4f, 1f, 1.0f, 42L,
                0, 0, 0, 0.3f, 0.35f, 0.6f, 2.0f);

        // Record original positions
        float origX0 = arc.x(0);
        float origY0 = arc.y(0);
        float origZ0 = arc.z(0);

        // Animate with non-zero strength
        NoiseAnimator.animate(arc, 1.0f, 0.5f, 0.27f, 2.0f, 42L);

        // Points should have moved
        boolean moved = false;
        for (int i = 0; i < arc.size(); i++) {
            if (Math.abs(arc.x(i) - origX0) > 1e-6f ||
                Math.abs(arc.y(i) - origY0) > 1e-6f ||
                Math.abs(arc.z(i) - origZ0) > 1e-6f) {
                moved = true;
                break;
            }
        }
        assertTrue(moved, "Points should move after noise animation");
    }

    @Test
    void animateZeroStrengthDoesNothing() {
        var arc = new ArcCurve();
        CurveGenerator.generate(arc, 0, 0, 0, 0, 1, 0, 0.01f, 12,
                0.8f, 0.2f, 0.4f, 1f, 1.0f, 42L,
                0, 0, 0, 0.3f, 0.35f, 0.6f, 2.0f);

        float origX5 = arc.x(5);
        float origY5 = arc.y(5);
        float origZ5 = arc.z(5);

        NoiseAnimator.animate(arc, 1.0f, 0.5f, 0f, 2.0f, 42L); // zero strength

        assertEquals(origX5, arc.x(5), 1e-6f);
        assertEquals(origY5, arc.y(5), 1e-6f);
        assertEquals(origZ5, arc.z(5), 1e-6f);
    }

    @Test
    void animateDeterministic() {
        var a = new ArcCurve();
        var b = new ArcCurve();
        CurveGenerator.generate(a, 0, 0, 0, 0, 1, 0, 0.01f, 12,
                0.8f, 0.2f, 0.4f, 1f, 1.0f, 42L,
                0, 0, 0, 0.3f, 0.35f, 0.6f, 2.0f);
        CurveGenerator.generate(b, 0, 0, 0, 0, 1, 0, 0.01f, 12,
                0.8f, 0.2f, 0.4f, 1f, 1.0f, 42L,
                0, 0, 0, 0.3f, 0.35f, 0.6f, 2.0f);

        NoiseAnimator.animate(a, 1.5f, 0.5f, 0.27f, 2.0f, 42L);
        NoiseAnimator.animate(b, 1.5f, 0.5f, 0.27f, 2.0f, 42L);

        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.x(i), b.x(i), 1e-6f);
            assertEquals(a.y(i), b.y(i), 1e-6f);
            assertEquals(a.z(i), b.z(i), 1e-6f);
        }
    }

    /** M29b 修复：噪声位移相对**基准位置**，反复动画不累积漂移（旧实现逐帧叠加 → 全弧同向飞走/拉长）。 */
    @Test
    void repeatedAnimationDoesNotDrift() {
        var arc = new ArcCurve();
        CurveGenerator.generate(arc, 0, 0, 0, 0, 1, 0, 0.01f, 12,
                0.8f, 0.2f, 0.4f, 1f, 1.0f, 42L,
                0, 0, 0, 0.3f, 0.35f, 0.6f, 2.0f);

        float[] baseX = new float[arc.size()];
        for (int i = 0; i < arc.size(); i++) baseX[i] = arc.x(i);

        // 模拟 60 帧连续动画
        for (int frame = 0; frame < 60; frame++) {
            NoiseAnimator.animate(arc, frame * (1f / 60f), 0.5f, 0.27f, 2.0f, 42L);
        }

        for (int i = 0; i < arc.size(); i++) {
            // 位移是围绕基准的**有界摆动**（噪声振幅 0.27×~1），不会累积成单向飞走
            assertTrue(Math.abs(arc.x(i) - baseX[i]) < 1.5f,
                    "point " + i + " should wobble near base, not drift: dx=" + (arc.x(i) - baseX[i]));
        }
    }
}
