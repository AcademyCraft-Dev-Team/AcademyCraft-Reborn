package org.academy.internal.common.ability.accelerator.skills.lv1;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KineticEnergyAppliedTest {
    @Test
    void clampsAndCyclesImpactLevel() {
        assertEquals(1, KineticEnergyApplied.clampImpactLevel(-5));
        assertEquals(6, KineticEnergyApplied.clampImpactLevel(9));
        assertEquals(2, KineticEnergyApplied.nextImpactLevel(1));
        assertEquals(1, KineticEnergyApplied.nextImpactLevel(6));
    }

    @Test
    void followsReferenceShockwaveScaling() {
        assertEquals(3.0f, KineticEnergyApplied.getImpactRadius(1));
        assertEquals(38.0f, KineticEnergyApplied.getImpactRadius(6));
        assertEquals(4.0f, KineticEnergyApplied.getImpactDamage(1, 1.0f, 1.0f));
        assertEquals(288.0f, KineticEnergyApplied.getImpactDamage(6, 2.0f, 1.0f));
    }

    @Test
    void coalescesClientMissAndServerHitFromOneSwing() {
        assertEquals(false, KineticEnergyApplied.isDistinctImpactTrigger(100, 100));
        assertEquals(false, KineticEnergyApplied.isDistinctImpactTrigger(100, 101));
        assertEquals(true, KineticEnergyApplied.isDistinctImpactTrigger(100, 102));
    }
}
