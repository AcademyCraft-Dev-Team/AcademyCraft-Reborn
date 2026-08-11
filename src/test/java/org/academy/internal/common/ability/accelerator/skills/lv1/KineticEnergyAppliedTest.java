package org.academy.internal.common.ability.accelerator.skills.lv1;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KineticEnergyAppliedTest {
    @Test
    void clampsAndCyclesImpactLevel() {
        assertEquals(1, KineticEnergyApplied.clampImpactLevel(-5));
        assertEquals(5, KineticEnergyApplied.clampImpactLevel(9));
        assertEquals(2, KineticEnergyApplied.nextImpactLevel(1));
        assertEquals(1, KineticEnergyApplied.nextImpactLevel(5));
    }

    @Test
    void followsReferenceShockwaveScaling() {
        assertEquals(3.0f, KineticEnergyApplied.getImpactRadius(1));
        assertEquals(27.0f, KineticEnergyApplied.getImpactRadius(5));
        assertEquals(5.0f, KineticEnergyApplied.getImpactDamage(1, 1.0f, 1.0f));
        assertEquals(58.0f, KineticEnergyApplied.getImpactDamage(5, 2.0f, 1.0f));
    }

    @Test
    void coalescesClientMissAndServerHitFromOneSwing() {
        assertEquals(false, KineticEnergyApplied.isDistinctImpactTrigger(100, 100));
        assertEquals(false, KineticEnergyApplied.isDistinctImpactTrigger(100, 101));
        assertEquals(true, KineticEnergyApplied.isDistinctImpactTrigger(100, 102));
    }
}
