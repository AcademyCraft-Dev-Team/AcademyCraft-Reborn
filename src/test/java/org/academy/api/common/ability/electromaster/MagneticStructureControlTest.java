package org.academy.api.common.ability.electromaster;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePhysicsControl;
import org.academy.api.common.structure.StructureMotionState;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureDynamics;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagneticStructureControlTest {
    @Test
    void offCenterPullBrakesTheRotatingAnchorEvenWhenTheCenterOfMassIsStationary() {
        var motion = new StructureMotionState(StructurePose.identity(Vec3.ZERO), Vec3.ZERO,
                new Vec3(0, 2, 0), Vec3.ZERO, 2.25, false, 0, 0);
        var localPoint = new Vec3(1, 0, 0);
        var force = MagneticStructureControl.pullForce(motion, localPoint, localPoint,
                Vec3.ZERO, 1.0, 0.65, 1.0);

        // omega cross radius moves this anchor toward -Z at 2 blocks/second.
        assertEquals(0.0, force.x, 1.0e-9);
        assertEquals(0.0, force.y, 1.0e-9);
        assertEquals(67.5, force.z, 1.0e-9);
        assertEquals(force, MagneticStructureControl.pullForce(StructureDynamics.State.from(motion),
                localPoint, localPoint, Vec3.ZERO, 1.0, 0.65, 1.0));
    }

    @Test
    void centerOfMassPullRetainsTheExistingMagneticVelocityProfile() {
        var center = new Vec3(0.5, 0.5, 0.5);
        var motion = new StructureMotionState(StructurePose.identity(new Vec3(3, 4, 5)),
                new Vec3(1, 2, 3), new Vec3(2, 3, 4), center, 8, false, 0, 0);
        var target = new Vec3(8, 9, 10);
        var expected = MagneticStructureControl.pullForce(motion.linearVelocity(),
                motion.pose().localToWorldPoint(center), target, Vec3.ZERO, 1.0, 0.65, 8, 0.5);

        assertEquals(expected, MagneticStructureControl.pullForce(motion, center, target,
                Vec3.ZERO, 1.0, 0.65, 0.5));
    }

    @Test
    void milestonesSelectBoundedMassBudgets() {
        assertEquals(128, MagneticStructureControl.massBudget(0));
        assertEquals(256, MagneticStructureControl.massBudget(1));
        assertEquals(512, MagneticStructureControl.massBudget(2));
        assertEquals(512, MagneticStructureControl.massBudget(3));
        assertEquals(128, MagneticStructureControl.massBudget(-1));
    }

    @Test
    void pullForceReproducesTheLegacyPerTickProfileInSolverUnits() {
        var force = MagneticStructureControl.pullForce(Vec3.ZERO, Vec3.ZERO, new Vec3(20, 0, 0),
                new Vec3(1, 0, 0), MagneticFieldTuning.TARGET_PULL_SPEED_PER_TICK, 0.65, 2.25);

        assertEquals(1656.0, force.x, 1.0e-9);
        assertEquals(0.0, force.y, 1.0e-9);
        assertEquals(0.0, force.z, 1.0e-9);
    }

    @Test
    void pullForceDeceleratesInsideTheStopDistance() {
        var force = MagneticStructureControl.pullForce(new Vec3(20, 0, 0), Vec3.ZERO, new Vec3(0.5, 0, 0),
                new Vec3(1, 0, 0), 1.0, 0.75, 1.0);

        assertEquals(-300.0, force.x, 1.0e-9);
    }

    @Test
    void pullForceHonorsTheSharedAbsoluteCeiling() {
        var force = MagneticStructureControl.pullForce(Vec3.ZERO, Vec3.ZERO, new Vec3(20, 0, 0),
                new Vec3(1, 0, 0), MagneticFieldTuning.TARGET_PULL_SPEED_PER_TICK, 0.65, 1000.0);

        assertTrue(force.length() <= StructurePhysicsControl.MAX_FORCE);
        assertTrue(force.length() > StructurePhysicsControl.MAX_FORCE - 0.01);
    }

    @Test
    void invalidMassIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> MagneticStructureControl.pullForce(Vec3.ZERO, Vec3.ZERO, new Vec3(1, 0, 0),
                        new Vec3(1, 0, 0), 1.0, 0.5, 0.0));
    }

    @Test
    void magneticParticipationIsTheBoundedMagneticCellRatio() {
        assertEquals(0.75, MagneticStructureControl.magneticParticipation(3, 4), 1.0e-9);
        assertEquals(0.125, MagneticStructureControl.magneticParticipation(1, 8), 1.0e-9);
        assertEquals(1.0, MagneticStructureControl.magneticParticipation(4, 4), 1.0e-9);
        assertEquals(1.0, MagneticStructureControl.magneticParticipation(0, 0), 1.0e-9);
        assertEquals(0.0, MagneticStructureControl.magneticParticipation(0, 8), 1.0e-9);
        assertEquals(1.0, MagneticStructureControl.magneticParticipation(99, 8), 1.0e-9);
        assertEquals(0.0, MagneticStructureControl.magneticParticipation(-3, 8), 1.0e-9);
    }

    @Test
    void programPullForceScalesTheConvertedProfileByParticipation() {
        var full = MagneticStructureControl.pullForce(Vec3.ZERO, Vec3.ZERO, new Vec3(20, 0, 0),
                new Vec3(1, 0, 0), MagneticFieldTuning.TARGET_PULL_SPEED_PER_TICK, 0.65, 2.25);
        var quarter = MagneticStructureControl.pullForce(Vec3.ZERO, Vec3.ZERO, new Vec3(20, 0, 0),
                new Vec3(1, 0, 0), MagneticFieldTuning.TARGET_PULL_SPEED_PER_TICK, 0.65, 2.25, 0.25);
        var none = MagneticStructureControl.pullForce(Vec3.ZERO, Vec3.ZERO, new Vec3(20, 0, 0),
                new Vec3(1, 0, 0), MagneticFieldTuning.TARGET_PULL_SPEED_PER_TICK, 0.65, 2.25, 0.0);

        assertEquals(full.x, quarter.x * 4.0, 1.0e-9);
        assertEquals(0.0, none.lengthSqr(), 1.0e-12);
        assertEquals(full, MagneticStructureControl.pullForce(Vec3.ZERO, Vec3.ZERO, new Vec3(20, 0, 0),
                new Vec3(1, 0, 0), MagneticFieldTuning.TARGET_PULL_SPEED_PER_TICK, 0.65, 2.25, 1.0));
    }
}
