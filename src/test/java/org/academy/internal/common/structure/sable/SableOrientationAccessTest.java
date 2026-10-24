package org.academy.internal.common.structure.sable;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SableOrientationAccessTest {
    @Test
    void positionRoundingBoundsTheObservedFloatComErrorPerAxis() {
        double x = -154.5416666790843, z = 57.47916665673256;
        assertTrue(Math.abs((double) (float) x - x) <= SableOrientationAccess.positionRounding(x));
        assertTrue(Math.abs((double) (float) z - z) <= SableOrientationAccess.positionRounding(z));
        assertEquals(7.62939453125E-6, SableOrientationAccess.positionRounding(x));
        assertEquals(1.9073486328125E-6, SableOrientationAccess.positionRounding(z));
    }

    @Test
    void roundingDoesNotDisappearAtExponentBoundariesOrGrowBeyondOneMillimetre() {
        assertTrue(SableOrientationAccess.positionRounding(1024) <= 1E-4);
        assertEquals(1.220703125E-4, SableOrientationAccess.positionRounding(2048));
        assertTrue(SableOrientationAccess.positionRounding(-2137) >= 0.0001200661063);
        assertEquals(1E-3, SableOrientationAccess.positionRounding(-30_000_000));
        assertEquals(0, SableOrientationAccess.positionRounding(Double.NaN));
        assertEquals(0, SableOrientationAccess.positionRounding(Double.POSITIVE_INFINITY));
    }

    @Test
    void inertiaBoundIncludesOffDiagonalTermsOfAnAsymmetricAssembly() {
        var tensor = new Matrix3d(10, -4, 0, -4, 10, 0, 0, 0, 2);
        double bound = SableOrientationAccess.inertiaBound(tensor);
        assertEquals(14, bound, 1.0e-12);
        // The strongest principal direction is not a grid axis: max(diagonal) would under-budget torque.
        var strongest = new Vector3d(1, -1, 0).normalize();
        assertEquals(14, tensor.transform(new Vector3d(strongest)).length(), 1.0e-12);
        for (int i = 0; i < 30; i++) {
            var axis = new Vector3d(Math.sin(i * 0.61), Math.cos(i * 0.37), Math.cos(i * 0.11)).normalize();
            assertTrue(tensor.transform(new Vector3d(axis)).length() <= bound + 1.0e-12);
        }
    }

    @Test
    void invalidInertiaCannotAuthorizeAnUnboundedCorrection() {
        assertThrows(IllegalArgumentException.class, () -> SableOrientationAccess.inertiaBound(new Matrix3d().zero()));
        assertThrows(IllegalArgumentException.class,
                () -> SableOrientationAccess.inertiaBound(new Matrix3d().m01(Double.NaN)));
        assertThrows(IllegalArgumentException.class,
                () -> SableOrientationAccess.inertiaBound(new Matrix3d().m22(Double.POSITIVE_INFINITY)));
    }

    @Test
    void customShapesUseThePinnedColliderCellClippingWithoutMakingAnOuterBox() {
        var shapes = SableOrientationAccess.clipToCell(List.of(
                new AABB(0.375, 0, 0.375, 0.625, 1.5, 0.625),
                new AABB(-0.2, 0.2, 0.4, 0.4, 0.8, 1.4),
                new AABB(2, 0, 0, 3, 1, 1)));
        assertEquals(List.of(new AABB(0.375, 0, 0.375, 0.625, 1, 0.625),
                new AABB(0, 0.2, 0.4, 0.4, 0.8, 1)), shapes);
        assertThrows(UnsupportedOperationException.class, () -> shapes.clear());
    }

    @Test
    void broadPhaseIncludesIncomingFastBodiesOutsideTheCurrentBox() {
        var current = new AABB(10, 0, 0, 11, 1, 1);
        var controlBounds = new AABB(0, 0, 0, 1, 1, 1);
        assertFalse(current.intersects(controlBounds));
        var swept = SableOrientationAccess.motionBounds(current, current.getCenter(), new Vec3(-200, 0, 0));
        assertTrue(swept.intersects(controlBounds));
    }

    @Test
    void broadPhaseIncludesRotationAboutAnOffsetCenterOfMass() {
        var beam = new AABB(0, 0, 0, 10, 1, 1);
        var center = new Vec3(0.5, 0.5, 0.5);
        var swept = SableOrientationAccess.motionBounds(beam, center, Vec3.ZERO);
        assertTrue(swept.contains(new Vec3(0.5, 9.5, 0.5)));
        assertTrue(swept.contains(new Vec3(-8.5, 0.5, 0.5)));
    }

    @Test
    void mountedContraptionsUseTheirOwnExtentAndRemainLocalToTheQuery() {
        var beam = new AABB(0, 0, 0, 12, 1, 1);
        var pivot = new Vec3(0.5, 0.5, 0.5);
        var nearby = SableOrientationAccess.pivotMotionBounds(beam, pivot,
                new Vec3(20, 5, 0), new Vec3(18, 5, 0));
        assertTrue(nearby.intersects(new AABB(7, 4, -1, 9, 6, 1)));
        var far = SableOrientationAccess.pivotMotionBounds(beam, pivot,
                new Vec3(1_000, 5, 0), new Vec3(998, 5, 0));
        assertFalse(far.intersects(new AABB(7, 4, -1, 9, 6, 1)));
    }

    @Test
    void velocityCorrectionTracksTheLatestSubstepInsteadOfRepeatedlyAddingTheAbsoluteTarget() {
        var target = new Vec3(0.5, -2, 3);
        var initial = new Vec3(0.1, 0.2, -0.5);
        var correction = SableOrientationAccess.angularCorrection(initial, target);
        assertEquals(target.x, initial.add(correction).x, 1.0e-12);
        assertEquals(target.y, initial.add(correction).y, 1.0e-12);
        assertEquals(target.z, initial.add(correction).z, 1.0e-12);
        assertEquals(Vec3.ZERO, SableOrientationAccess.angularCorrection(target, target));
        assertThrows(IllegalArgumentException.class,
                () -> SableOrientationAccess.angularCorrection(Vec3.ZERO, new Vec3(0, Double.NaN, 0)));
    }
}
