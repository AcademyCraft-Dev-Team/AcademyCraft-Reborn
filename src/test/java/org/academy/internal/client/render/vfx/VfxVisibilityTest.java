package org.academy.internal.client.render.vfx;

import net.minecraft.world.phys.Vec3;
import org.academy.api.client.render.vfx.VfxCamera;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VfxVisibilityTest {
    private static final Vec3 CAMERA = new Vec3(20000, 80, -20000);

    private static VfxCamera camera(float yaw) {
        return new VfxCamera(CAMERA.toVector3f(), new Quaternionf(),
                new Matrix4f().setPerspective((float) Math.toRadians(70), 16f / 9f, 0.1f, 512),
                new Matrix4f().rotateY(yaw), 70);
    }

    @Test
    void beamCrossingViewStaysVisibleWithBothEndpointsOffscreen() {
        var view = camera(0);
        var left = CAMERA.add(-300, 0, -20);
        var right = CAMERA.add(300, 0, -20);
        assertFalse(VfxVisibility.sphere(view, left, 2));
        assertFalse(VfxVisibility.sphere(view, right, 2));
        assertTrue(VfxVisibility.segment(view, left, right, 2));
    }

    @Test
    void nearbyShockwaveEnclosingCameraSurvivesNearPlane() {
        assertTrue(VfxVisibility.sphere(camera(0), CAMERA.add(0, 0, 1), 8));
    }

    @Test
    void offscreenBeamIsCulledAndReappearsAfterTurning() {
        var start = CAMERA.add(0, 0, 20);
        var end = CAMERA.add(0, 0, 100);
        assertFalse(VfxVisibility.segment(camera(0), start, end, 2));
        assertTrue(VfxVisibility.segment(camera((float) Math.PI), start, end, 2));
    }

    @Test
    void plasmaFocusUsesTranslatedBoundsAtCasterView() {
        var budget = new org.academy.api.client.render.vfxgraph.runtime.EffectBudget();
        var view = camera(0);
        var focus = CAMERA.add(0, 31, -6).toVector3f();
        assertTrue(budget.sphereInFrustum(view.projectionMatrix(), view.viewRotationMatrix(),
                view.pos(), focus, 32));
        assertFalse(budget.sphereInFrustum(view.projectionMatrix(), view.viewRotationMatrix(),
                view.pos(), new Vector3f(), 32));
    }
}
