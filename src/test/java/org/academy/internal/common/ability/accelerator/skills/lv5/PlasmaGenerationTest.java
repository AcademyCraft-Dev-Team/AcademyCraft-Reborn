package org.academy.internal.common.ability.accelerator.skills.lv5;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlasmaGenerationTest {
    @Test
    void chargeProgressIsServerBounded() {
        assertEquals(0.0f, PlasmaGeneration.getChargeProgress(100, 90), 0.0001f);
        assertEquals(0.5f, PlasmaGeneration.getChargeProgress(100, 130), 0.0001f);
        assertEquals(1.0f, PlasmaGeneration.getChargeProgress(100, 160), 0.0001f);
    }

    @Test
    void releaseUsesTheReferenceFixedDamageAndArea() {
        assertEquals(200.0f, PlasmaGeneration.calculateDamage(0), 0.0001f);
        assertEquals(200.0f, PlasmaGeneration.calculateDamage(60), 0.0001f);
        assertEquals(20.0f, PlasmaGeneration.calculateExplosionRadius(0), 0.0001f);
        assertEquals(20.0f, PlasmaGeneration.calculateExplosionRadius(60), 0.0001f);
    }

    @Test
    void referenceRuntimeConstantsStayAligned() {
        assertEquals(60, PlasmaGeneration.MAX_CHARGE_TICKS);
        assertEquals(20, PlasmaGeneration.CP_PER_SECOND);
        assertEquals(2.5, PlasmaGeneration.TRAVEL_SPEED, 0.0001);
        assertEquals(20.0f, PlasmaGeneration.EXPLOSION_POWER, 0.0001f);
    }
}
