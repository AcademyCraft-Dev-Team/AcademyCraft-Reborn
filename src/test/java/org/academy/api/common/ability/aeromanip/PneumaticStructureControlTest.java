package org.academy.api.common.ability.aeromanip;

import org.academy.api.common.structure.StructurePhysicsControl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PneumaticStructureControlTest {
    @Test void compoundBodyHasOneBudgetAndMassAwareResponse() {
        var single = PneumaticStructureControl.settings(2.25, 0, 0, 24);
        var compound = PneumaticStructureControl.settings(50, 0, 0, 24);
        assertTrue(compound.maxMass() >= 50);
        assertEquals(120, single.stiffness());
        assertEquals(36, single.damping());
        assertEquals(1000, single.maxForce());
        assertEquals(single.stiffness() / 2.25, compound.stiffness() / 50, 1.0E-9);
        assertEquals(single.damping() / 2.25, compound.damping() / 50, 1.0E-9);
        assertTrue(compound.maxForce() > 50 * 10); // One multi-cell body can oppose gravity.
        assertTrue(PneumaticStructureControl.resourceMultiplier(50) > PneumaticStructureControl.resourceMultiplier(2));
    }

    @Test void levelCeilingsRemainFiniteAndOversizeBodiesRemainRejectable() {
        var settings = PneumaticStructureControl.settings(10_000, 2, 3, 24);
        assertEquals(512, settings.maxMass());
        assertTrue(settings.maxForce() <= StructurePhysicsControl.MAX_FORCE);
        assertTrue(settings.stiffness() <= StructurePhysicsControl.MAX_GAIN);
        assertTrue(settings.damping() <= StructurePhysicsControl.MAX_GAIN);
        assertEquals(16, PneumaticStructureControl.resourceMultiplier(10_000));
        for (var mass : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> PneumaticStructureControl.settings(mass, 0, 0, 24));
            assertThrows(IllegalArgumentException.class, () -> PneumaticStructureControl.resourceMultiplier(mass));
        }
    }
}
