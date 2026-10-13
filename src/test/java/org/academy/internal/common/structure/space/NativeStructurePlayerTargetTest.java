package org.academy.internal.common.structure.space;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeStructurePlayerTargetTest {
    @Test
    void originalRayIgnoresEntitiesBehindTheSelectedFace() {
        var eye = Vec3.ZERO;
        var end = new Vec3(0, 0, 6);
        assertTrue(NativeStructurePlayerTarget.occludes(new AABB(-0.2, -0.2, 2, 0.2, 0.2, 3), 0, eye, end, 16));
        assertFalse(NativeStructurePlayerTarget.occludes(new AABB(-0.2, -0.2, 5, 0.2, 0.2, 6), 0, eye, end, 16));
    }

    @Test
    void reprojectedRayCanBeOccludedWhileTheOriginalRayIsClear() {
        var eye = Vec3.ZERO;
        var currentPoint = new Vec3(0.75, 0, 4);
        var obstruction = new AABB(0.3, -0.1, 2, 0.45, 0.1, 2.2);
        assertFalse(NativeStructurePlayerTarget.occludes(obstruction, 0, eye, new Vec3(0, 0, 6), 16));
        assertTrue(NativeStructurePlayerTarget.occludes(obstruction, 0, eye, currentPoint, currentPoint.lengthSqr()));
    }

    @Test
    void eyeInsideAndCoincidentEntityFacesWinOverTheStructure() {
        var eye = Vec3.ZERO;
        var point = new Vec3(0, 0, 4);
        assertTrue(NativeStructurePlayerTarget.occludes(new AABB(-1, -1, -1, 1, 1, 1), 0, eye, point, 16));
        assertTrue(NativeStructurePlayerTarget.occludes(new AABB(-1, -1, 4, 1, 1, 5), 0, eye, new Vec3(0, 0, 6), 16));
        assertTrue(NativeStructurePlayerTarget.occludes(new AABB(-1, -1, 4, 1, 1, 5), 0, eye, point, 16));
        assertTrue(NativeStructurePlayerTarget.occludes(new AABB(-1, -1, -5, 1, 1, -4), 0, eye, point.scale(-1), 16));
        assertTrue(NativeStructurePlayerTarget.occludes(new AABB(0.1, -1, 1, 0.2, 1, 2), 0.15, eye, point, 16));
    }

    @Test
    void malformedEntityRadiusCannotAuthorizeAnAction() {
        var bounds = new AABB(-1, -1, 1, 1, 1, 2);
        assertTrue(NativeStructurePlayerTarget.occludes(bounds, Double.NaN, Vec3.ZERO, new Vec3(0, 0, 4), 16));
        assertTrue(NativeStructurePlayerTarget.occludes(bounds, -1, Vec3.ZERO, new Vec3(0, 0, 4), 16));
        assertTrue(NativeStructurePlayerTarget.occludes(new AABB(Double.NaN, 0, 1, 1, 1, 2),
                0, Vec3.ZERO, new Vec3(0, 0, 4), 16));
    }
}
