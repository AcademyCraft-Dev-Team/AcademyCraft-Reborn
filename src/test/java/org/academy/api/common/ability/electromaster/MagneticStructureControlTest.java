package org.academy.api.common.ability.electromaster;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePhysicsControl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagneticStructureControlTest {
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
}
