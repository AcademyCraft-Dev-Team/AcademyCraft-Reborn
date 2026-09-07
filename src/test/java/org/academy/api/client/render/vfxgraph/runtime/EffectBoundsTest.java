package org.academy.api.client.render.vfxgraph.runtime;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EffectBoundsTest {
    @Test void radiusKeepsVisibleEdgeWhenCenterExceedsDistance() {
        var budget = new EffectBudget();
        assertTrue(budget.shouldRender(new Vector3f(), new Vector3f(120, 0, 0), 32, 96));
        assertFalse(budget.shouldRender(new Vector3f(), new Vector3f(140, 0, 0), 32, 96));
    }
    @Test void translatedCameraAndProjectionChangesDoNotReuseStaleFrustum() {
        var budget = new EffectBudget();
        var projection = new Matrix4f().setPerspective((float) Math.toRadians(70), 1, 0.1f, 500);
        var view = new Matrix4f();
        var camera = new Vector3f(20000, 80, -20000);
        var center = new Vector3f(camera).add(0, 0, -50);
        assertTrue(budget.sphereInFrustum(projection, view, camera, center, 2));
        view.rotateY((float) Math.PI);
        assertFalse(budget.sphereInFrustum(projection, view, camera, center, 2));
        assertTrue(budget.sphereInFrustum(projection, view, camera, center, 100));
    }
}
