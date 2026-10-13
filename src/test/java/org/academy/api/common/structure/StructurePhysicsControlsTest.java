package org.academy.api.common.structure;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StructurePhysicsControlsTest {
    @Test
    void forceIsWorldSpaceAndDoesNotDependOnMassOrOrientation() {
        var requested = new Vec3(2, -3, 4);
        var control = new StructurePhysicsControl.Force(new Vec3(1, 2, 3), requested);
        var motion = motion(StructurePose.fromYawAroundPivot(new Vec3(20, 30, 40), 90, Vec3.ZERO),
                new Vec3(10, 20, 30), new Vec3(1, 2, 3), new Vec3(2, 0, 0), 50);
        assertSame(requested, StructurePhysicsControls.force(control, motion));
    }

    @Test
    void dampingUsesVelocityAtThePointAboutActualCenterOfMass() {
        var pose = StructurePose.identity(new Vec3(10, 20, 30));
        var point = new Vec3(3, 0, 0);
        var motion = motion(pose, new Vec3(1, 0, 0), new Vec3(0, 0, 2), new Vec3(2, 0, 0), 1);
        var control = new StructurePhysicsControl.Anchor(point, pose.localToWorldPoint(point), Vec3.ZERO, 2, 3, 100);
        assertVector(new Vec3(1, 2, 0), StructurePhysicsControls.pointVelocity(point, motion), 1.0e-12);
        assertVector(new Vec3(-3, -6, 0), StructurePhysicsControls.force(control, motion), 1.0e-12);
    }

    @Test
    void rotationTransformsTheMomentArmBeforeCrossingWorldAngularVelocity() {
        var pose = StructurePose.fromYawAroundPivot(new Vec3(20, 30, 40), 90, Vec3.ZERO);
        var point = new Vec3(3, 0, 0);
        var motion = motion(pose, new Vec3(1, 0, 0), new Vec3(0, 2, 0), new Vec3(2, 0, 0), 1);
        var control = new StructurePhysicsControl.Anchor(point, pose.localToWorldPoint(point), Vec3.ZERO, 2, 3, 100);
        assertVector(new Vec3(3, 0, 0), StructurePhysicsControls.pointVelocity(point, motion), 1.0e-12);
        assertVector(new Vec3(-9, 0, 0), StructurePhysicsControls.force(control, motion), 1.0e-12);
    }

    @Test
    void springAndTargetVelocityContributeInPerSecondUnits() {
        var motion = motion(StructurePose.identity(Vec3.ZERO), new Vec3(2, 0, 0), Vec3.ZERO, Vec3.ZERO, 1);
        var control = new StructurePhysicsControl.Anchor(Vec3.ZERO, new Vec3(3, 0, 0),
                new Vec3(4, 0, 0), 2, 3, 100);
        assertVector(new Vec3(12, 0, 0), StructurePhysicsControls.force(control, motion), 1.0e-12);
        assertEquals(new Vec3(3, 0, 0), control.targetPosition(), "Target velocity must not advance the target implicitly");
    }

    @Test
    void forceLimitPreservesDirectionAndLimitsVectorNorm() {
        var motion = resting(1);
        var control = new StructurePhysicsControl.Anchor(Vec3.ZERO, new Vec3(3, 4, 0), Vec3.ZERO, 2, 0, 5);
        var force = StructurePhysicsControls.force(control, motion);
        assertVector(new Vec3(3, 4, 0), force, 1.0e-12);
        assertEquals(5, force.length(), 1.0e-12);
    }

    @Test
    void massAndGravityAreNotImplicitControllerInputs() {
        var atTarget = new StructurePhysicsControl.Anchor(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 10, 4, 100);
        assertEquals(Vec3.ZERO, StructurePhysicsControls.force(atTarget, resting(1)));
        assertEquals(Vec3.ZERO, StructurePhysicsControls.force(atTarget, resting(100)));
        var displaced = new StructurePhysicsControl.Anchor(Vec3.ZERO, new Vec3(0, 2, 0), Vec3.ZERO, 10, 4, 100);
        assertVector(new Vec3(0, 20, 0), StructurePhysicsControls.force(displaced, resting(1)), 0);
        assertVector(new Vec3(0, 20, 0), StructurePhysicsControls.force(displaced, resting(100)), 0);
    }

    @Test
    void commonWorldTranslationAndVelocityDoNotChangeTheForce() {
        var point = new Vec3(3, 0, 0);
        var center = new Vec3(2, 0, 0);
        var origin = new Vec3(10, 20, 30);
        var translation = new Vec3(1_000, -300, 2_000);
        var velocity = new Vec3(1, 2, 3);
        var addedVelocity = new Vec3(4, 5, 6);
        var angular = new Vec3(0, 0, 2);
        var target = new Vec3(15, 23, 32);
        var targetVelocity = new Vec3(2, 4, 3);
        var a = new StructurePhysicsControl.Anchor(point, target, targetVelocity, 2, 3, 100);
        var b = new StructurePhysicsControl.Anchor(point, target.add(translation), targetVelocity.add(addedVelocity), 2, 3, 100);
        var first = motion(StructurePose.identity(origin), velocity, angular, center, 1);
        var second = motion(StructurePose.identity(origin.add(translation)), velocity.add(addedVelocity), angular, center, 1);
        assertVector(StructurePhysicsControls.force(a, first), StructurePhysicsControls.force(b, second), 1.0e-12);
    }

    @Test
    void finiteHugeForceClampsWithoutOverflowOrLosingDirection() {
        var motion = motion(StructurePose.identity(Vec3.ZERO),
                new Vec3(Double.MAX_VALUE, Double.MAX_VALUE, 0), Vec3.ZERO, Vec3.ZERO, 1);
        var control = new StructurePhysicsControl.Anchor(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 1, 1, 10);
        var force = StructurePhysicsControls.force(control, motion);
        assertVector(new Vec3(-10 / Math.sqrt(2), -10 / Math.sqrt(2), 0), force, 1.0e-12);
        assertEquals(10, force.length(), 1.0e-12);
    }

    @Test
    void nonFiniteIntermediateIsRejectedInsteadOfHiddenByForceLimit() {
        var overflowVelocity = motion(StructurePose.identity(Vec3.ZERO),
                new Vec3(Double.MAX_VALUE, 0, 0), Vec3.ZERO, Vec3.ZERO, 1);
        var control = new StructurePhysicsControl.Anchor(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 1, 2, 1);
        assertThrows(IllegalArgumentException.class, () -> StructurePhysicsControls.force(control, overflowVelocity));
        var overflowRotation = motion(StructurePose.identity(Vec3.ZERO), Vec3.ZERO,
                new Vec3(0, 0, Double.MAX_VALUE), Vec3.ZERO, 1);
        var offCenter = new StructurePhysicsControl.Anchor(new Vec3(2, 0, 0), new Vec3(2, 0, 0), Vec3.ZERO, 1, 1, 1);
        assertThrows(IllegalArgumentException.class, () -> StructurePhysicsControls.force(offCenter, overflowRotation));
    }

    @Test
    void tinyPositiveGainsAndZeroResultRemainFinite() {
        var control = new StructurePhysicsControl.Anchor(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO,
                Double.MIN_VALUE, 0, Double.MIN_VALUE);
        assertEquals(Vec3.ZERO, StructurePhysicsControls.force(control, resting(1)));
    }

    @Test
    void missingObservationIsNotReplacedByAStationaryDefault() {
        var control = new StructurePhysicsControl.Force(Vec3.ZERO, Vec3.ZERO);
        assertThrows(NullPointerException.class, () -> StructurePhysicsControls.force(control, null));
        assertThrows(NullPointerException.class, () -> StructurePhysicsControls.force(null, resting(1)));
        assertThrows(NullPointerException.class, () -> StructurePhysicsControls.pointVelocity(null, resting(1)));
        assertThrows(NullPointerException.class, () -> StructurePhysicsControls.pointVelocity(Vec3.ZERO, null));
    }

    private static StructureMotionState resting(double mass) {
        return motion(StructurePose.identity(Vec3.ZERO), Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, mass);
    }

    private static StructureMotionState motion(StructurePose pose, Vec3 linear, Vec3 angular, Vec3 center, double mass) {
        return new StructureMotionState(pose, linear, angular, center, mass, false, 0, 0);
    }

    private static void assertVector(Vec3 expected, Vec3 actual, double tolerance) {
        assertEquals(expected.x, actual.x, tolerance);
        assertEquals(expected.y, actual.y, tolerance);
        assertEquals(expected.z, actual.z, tolerance);
    }
}
