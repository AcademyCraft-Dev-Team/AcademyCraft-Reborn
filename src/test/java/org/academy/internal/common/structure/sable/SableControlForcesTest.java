package org.academy.internal.common.structure.sable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructureMotionState;
import org.academy.api.common.structure.StructurePhysicsControl;
import org.academy.api.common.structure.StructurePose;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SableControlForcesTest {
    @Test
    void rotatedImpulseReturnsToTheRequestedWorldDirectionAndUsesSeconds() {
        var pose = StructurePose.fromYawAroundPivot(new Vec3(25, 60, -40), 90, Vec3.ZERO);
        var point = new Vec3(2, 0, 0);
        var motion = motion(pose, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 17);
        var control = new StructurePhysicsControl.Anchor(point, pose.localToWorldPoint(point).add(0, 0, 10),
                Vec3.ZERO, 120, 36, 1000);
        var impulse = SableControlForces.localImpulse(control, motion, 0.01);
        assertVector(new Vec3(0, 0, 10), pose.localToWorldDirection(impulse));
        assertEquals(10, impulse.length(), 1.0E-10);
    }

    @Test
    void subdivisionPreservesIntegratedImpulseForTheSameObservation() {
        var pose = StructurePose.identity(Vec3.ZERO);
        var control = new StructurePhysicsControl.Anchor(Vec3.ZERO, new Vec3(2, 3, 4), Vec3.ZERO, 120, 36, 1000);
        var state = motion(pose, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 1);
        assertVector(SableControlForces.localImpulse(control, state, 0.05),
                SableControlForces.localImpulse(control, state, 0.005).scale(10));
        var heavy = motion(pose, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 9000);
        assertVector(SableControlForces.localImpulse(control, state, 0.05),
                SableControlForces.localImpulse(control, heavy, 0.05));
    }

    @Test
    void pointDampingUsesActualCenterOfMassWithoutShiftingThePlotApplicationPoint() {
        var localPoint = new Vec3(3, 0, 0);
        var pose = StructurePose.identity(new Vec3(20, 30, 40));
        var state = motion(pose, new Vec3(1, 0, 0), new Vec3(0, 0, 2), new Vec3(2, 0, 0), 30);
        var control = new StructurePhysicsControl.Anchor(localPoint, pose.localToWorldPoint(localPoint),
                Vec3.ZERO, 120, 3, 1000);
        assertVector(new Vec3(-0.15, -0.3, 0), SableControlForces.localImpulse(control, state, 0.05));
        assertEquals(new Vec3(1_000_003, 80, -1_000_000),
                SableControlForces.plotPoint(new BlockPos(1_000_000, 80, -1_000_000), localPoint));
    }

    @Test
    void invalidSubstepsCannotApplyNegativeOrUnboundedImpulses() {
        var state = motion(StructurePose.identity(Vec3.ZERO), Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 1);
        var control = new StructurePhysicsControl.Anchor(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 120, 36, 1000);
        for (double seconds : new double[]{0, -0.01, 0.051, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> SableControlForces.localImpulse(control, state, seconds));
        }
    }

    @Test
    void leaseExpiresOnServerTicksIncludingTheSignedCounterWrap() {
        assertFalse(SableControlForces.expired(101, 100, 3));
        assertFalse(SableControlForces.expired(102, 100, 3));
        assertTrue(SableControlForces.expired(103, 100, 3));
        assertFalse(SableControlForces.expired(Integer.MIN_VALUE, Integer.MAX_VALUE, 3));
        assertTrue(SableControlForces.expired(Integer.MIN_VALUE + 2, Integer.MAX_VALUE, 3));
    }

    private static StructureMotionState motion(StructurePose pose, Vec3 linear, Vec3 angular, Vec3 center, double mass) {
        return new StructureMotionState(pose, linear, angular, center, mass, false, 0, 0);
    }

    private static void assertVector(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, 1.0E-10);
        assertEquals(expected.y, actual.y, 1.0E-10);
        assertEquals(expected.z, actual.z, 1.0E-10);
    }
}
