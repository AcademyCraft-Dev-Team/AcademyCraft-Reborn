package org.academy.internal.common.ability.aeromanip.movement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HighSpeedJetStructureServiceTest {
    @Test
    void impactRequiresClosingSpeedAndUsesBoundedDamage() {
        assertEquals(0.0f,
                HighSpeedJetStructureService.impactDamage(4, 0.34, 1.0f));
        assertEquals(2.4f,
                HighSpeedJetStructureService.impactDamage(4, 1.0, 1.0f),
                1.0e-6f);
        assertEquals(24.0f,
                HighSpeedJetStructureService.impactDamage(256, 100.0, 1.0f),
                1.0e-6f);
    }

    @Test
    void knockbackUsesTheSameImpactGateAndCap() {
        assertEquals(0.0,
                HighSpeedJetStructureService.knockbackStrength(4, 0.34, 1.0f));
        assertEquals(0.64,
                HighSpeedJetStructureService.knockbackStrength(4, 1.0, 1.0f),
                1.0e-9);
        assertEquals(2.4,
                HighSpeedJetStructureService.knockbackStrength(256, 100.0, 1.0f),
                1.0e-9);
    }
}
