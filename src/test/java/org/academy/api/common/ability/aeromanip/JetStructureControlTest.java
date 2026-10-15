package org.academy.api.common.ability.aeromanip;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePhysicsControl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JetStructureControlTest {
    @Test
    void legacySpeedCeilingsConvertToBlocksPerSecond() {
        assertEquals(60.0, JetStructureControl.maximumSpeed(0.0f), 1.0e-9);
        assertEquals(120.0, JetStructureControl.maximumSpeed(1.0f), 1.0e-9);
        assertEquals(160.0, JetStructureControl.maximumSpeed(2.0f), 1.0e-9);
    }

    @Test
    void legacyThrustConvertsWithMassNormalizationAndMilestones() {
        var standard = JetStructureControl.thrustForce(new Vec3(0.0, 0.0, 1.0), 1.0, 1.0f, 0);
        assertEquals(192.0, standard.length(), 1.0e-9);
        var milestone = JetStructureControl.thrustForce(new Vec3(0.0, 0.0, 1.0), 1.0, 1.0f, 3);
        assertEquals(240.0, milestone.length(), 1.0e-9);
        assertEquals(1.0, milestone.normalize().z, 1.0e-9);
    }

    @Test
    void thrustHonorsTheLevelAbsoluteForceCeiling() {
        var clamped = JetStructureControl.thrustForce(new Vec3(0.0, 0.0, 1.0), 100.0, 1.0f, 0);
        assertEquals(StructurePhysicsControl.MAX_FORCE, clamped.length(), 1.0e-9);
    }

    @Test
    void limitedThrustReproducesTheLegacyPerTickIntegration() {
        var launch = JetStructureControl.limitedThrustForce(
                new Vec3(0.0, 0.0, 1.0), Vec3.ZERO, 1.0, 1.0f, 0);
        assertEquals(192.0, launch.z, 1.0e-9);
        var nearCeiling = JetStructureControl.limitedThrustForce(
                new Vec3(0.0, 0.0, 1.0), new Vec3(0.0, 0.0, 119.5), 1.0, 1.0f, 0);
        assertEquals(0.5 / JetStructureControl.PHYSICS_STEP_SECONDS,
                nearCeiling.z, 1.0e-6);
        var overCeiling = JetStructureControl.limitedThrustForce(
                new Vec3(0.0, 0.0, 1.0), new Vec3(0.0, 0.0, 130.0), 1.0, 1.0f, 0);
        assertEquals(-200.0, overCeiling.z, 1.0e-6);
    }

    @Test
    void combinedGroupSplitsOneSharedBudgetAndCancelsSymmetricDirections() {
        var one = JetStructureControl.combinedThrustForce(
                List.of(new Vec3(0.0, 0.0, 1.0)), 1.0, 1.0f, 0);
        assertEquals(192.0, one.length(), 1.0e-9);
        var parallel = JetStructureControl.combinedThrustForce(
                List.of(new Vec3(0.0, 0.0, 1.0), new Vec3(0.0, 0.0, 1.0)), 1.0, 1.0f, 0);
        assertEquals(192.0, parallel.length(), 1.0e-9,
                "two parallel anchors must not multiply the shared budget");
        var cancelled = JetStructureControl.combinedThrustForce(
                List.of(new Vec3(0.0, 0.0, 1.0), new Vec3(0.0, 0.0, -1.0)), 1.0, 1.0f, 0);
        assertEquals(0.0, cancelled.length(), 1.0e-9,
                "symmetric anchors must cancel exactly");
        var orthogonal = JetStructureControl.combinedThrustForce(
                List.of(new Vec3(0.0, 0.0, 1.0), new Vec3(1.0, 0.0, 0.0)), 1.0, 1.0f, 0);
        assertEquals(96.0 * Math.sqrt(2.0), orthogonal.length(), 1.0e-9);
        assertEquals(0.0, JetStructureControl.combinedThrustForce(
                List.of(), 1.0, 1.0f, 0).length(), 0.0);
        assertEquals(0.0, JetStructureControl.combinedThrustForce(
                List.of(Vec3.ZERO), 1.0, 1.0f, 0).length(), 0.0);
    }

    @Test
    void combinedGroupReproducesTheCeilingIntegrationExactlyOnce() {
        var launch = JetStructureControl.limitedCombinedThrustForce(
                List.of(new Vec3(0.0, 0.0, 1.0), new Vec3(0.0, 0.0, 1.0)),
                Vec3.ZERO, 1.0, 1.0f, 0);
        assertEquals(192.0, launch.z, 1.0e-9);
        var cancelled = JetStructureControl.limitedCombinedThrustForce(
                List.of(new Vec3(0.0, 0.0, 1.0), new Vec3(0.0, 0.0, -1.0)),
                Vec3.ZERO, 1.0, 1.0f, 0);
        assertEquals(0.0, cancelled.length(), 1.0e-9);
        var overCeiling = JetStructureControl.limitedCombinedThrustForce(
                List.of(new Vec3(0.0, 0.0, 1.0), new Vec3(0.0, 0.0, 1.0)),
                new Vec3(0.0, 0.0, 130.0), 1.0, 1.0f, 0);
        assertEquals(-200.0, overCeiling.z, 1.0e-6);
    }

    @Test
    void thrustGroupAccountsTorqueWithoutASecondBudget() {
        var origin = new Vec3(0.0, 0.0, 0.0);
        var symmetric = JetStructureControl.thrustGroup(List.of(
                new JetStructureControl.ThrustAnchor(new Vec3(0.0, 0.0, -1.0), new Vec3(0.0, 0.0, 1.0)),
                new JetStructureControl.ThrustAnchor(new Vec3(0.0, 0.0, 1.0), new Vec3(0.0, 0.0, -1.0))
        ), origin, 1.0, 1.0f, 0);
        assertEquals(0.0, symmetric.totalForce().length(), 1.0e-9);
        assertEquals(0.0, symmetric.totalTorque().length(), 1.0e-9,
                "symmetric anchors must cancel their torque as well");
        var single = JetStructureControl.thrustGroup(List.of(
                new JetStructureControl.ThrustAnchor(new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 0.0, 1.0))
        ), origin, 1.0, 1.0f, 0);
        assertEquals(192.0, single.totalForce().length(), 1.0e-9,
                "one anchor receives the full shared budget");
        assertEquals(0.0, single.totalTorque().x, 1.0e-9);
        assertEquals(-192.0, single.totalTorque().y, 1.0e-9,
                "torque must be the derived Sigma(r x F), not a second budget");
        assertEquals(0.0, single.totalTorque().z, 1.0e-9);
    }

    @Test
    void invalidArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> JetStructureControl.thrustForce(Vec3.ZERO, 1.0, 1.0f, 0));
        assertThrows(IllegalArgumentException.class,
                () -> JetStructureControl.thrustForce(new Vec3(0.0, 1.0, 0.0), 0.0, 1.0f, 0));
        assertThrows(IllegalArgumentException.class,
                () -> JetStructureControl.limitedThrustForce(new Vec3(0.0, 1.0, 0.0),
                        new Vec3(Double.NaN, 0.0, 0.0), 1.0, 1.0f, 0));
    }
}
