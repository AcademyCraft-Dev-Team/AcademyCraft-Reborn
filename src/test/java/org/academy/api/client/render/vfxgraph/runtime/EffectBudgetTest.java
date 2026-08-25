package org.academy.api.client.render.vfxgraph.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class EffectBudgetTest {
    private static final Vector3f ORIGIN = new Vector3f(0f, 0f, 0f);

    private static boolean frustum(EffectBudget budget, Vector3f cameraPos, Vector3f effectPos) {
        var projection = new Matrix4f().setPerspective((float) Math.toRadians(70), 1f, 0.1f, 100f);
        return budget.sphereInFrustum(projection, new Matrix4f(), cameraPos, effectPos);
    }

    @Test
    void distanceCulling() {
        var budget = new EffectBudget();
        budget.setMaxRenderDistance(10f);
        assertTrue(budget.shouldRender(new Vector3f(0f, 0f, 0f), new Vector3f(5f, 0f, 0f)));
        assertTrue(budget.shouldRender(new Vector3f(0f, 0f, 0f), new Vector3f(10f, 0f, 0f)));
        assertFalse(budget.shouldRender(new Vector3f(0f, 0f, 0f), new Vector3f(15f, 0f, 0f)));
    }

    @Test
    void particleCap() {
        var budget = new EffectBudget();
        budget.setMaxParticlesPerEffect(100);
        assertTrue(budget.canSpawnMore(0));
        assertTrue(budget.canSpawnMore(99));
        assertFalse(budget.canSpawnMore(100));
        assertFalse(budget.canSpawnMore(150));
    }

    @Test
    void sphereInsideFrustumIsVisible() {
        var budget = new EffectBudget();
        assertTrue(frustum(budget, ORIGIN, new Vector3f(0f, 0f, -5f)));
    }

    @Test
    void sphereBehindCameraIsCulled() {
        var budget = new EffectBudget();
        budget.setEffectRadius(0.1f);
        assertFalse(frustum(budget, ORIGIN, new Vector3f(0f, 0f, 5f)));
    }

    @Test
    void sphereFarOutsideFrustumIsCulled() {
        var budget = new EffectBudget();
        assertFalse(frustum(budget, ORIGIN, new Vector3f(1000f, 0f, -5f)));
    }

    @Test
    void frustumCullingCanBeDisabled() {
        var budget = new EffectBudget();
        budget.setFrustumCullingEnabled(false);
        assertTrue(frustum(budget, ORIGIN, new Vector3f(1000f, 0f, -5f)));
    }

    @Test
    void cullingUsesCameraRelativePosition() {
        // Bug 修复回归：相机不在原点时，视锥须含相机平移（世界坐标效果直接测球）
        var budget = new EffectBudget();
        budget.setEffectRadius(0.1f);
        // 相机在 (0,0,10)，看向 -Z；效果在 (0,0,5)（相机前方 5）应可见
        assertTrue(frustum(budget, new Vector3f(0f, 0f, 10f), new Vector3f(0f, 0f, 5f)));
        // 效果在 (0,0,15)（相机后方 5）应被剔除
        assertFalse(frustum(budget, new Vector3f(0f, 0f, 10f), new Vector3f(0f, 0f, 15f)));
    }
}
