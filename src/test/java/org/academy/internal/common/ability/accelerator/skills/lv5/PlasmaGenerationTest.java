package org.academy.internal.common.ability.accelerator.skills.lv5;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlasmaGenerationTest {
    @Test
    void chargeProgressIsServerBounded() {
        assertEquals(0.0f, PlasmaGeneration.getChargeProgress(100, 90), 0.0001f);
        assertEquals(0.5f, PlasmaGeneration.getChargeProgress(100, 220), 0.0001f);
        assertEquals(1.0f, PlasmaGeneration.getChargeProgress(100, 340), 0.0001f);
    }

    @Test
    void releaseUsesFortyTickStagesForDamageAndArea() {
        assertEquals(0, PlasmaGeneration.calculateStage(39));
        assertEquals(1, PlasmaGeneration.calculateStage(40));
        assertEquals(6, PlasmaGeneration.calculateStage(999));
        assertEquals(0.0f, PlasmaGeneration.calculateDamage(39), 0.0001f);
        assertEquals(50.0f, PlasmaGeneration.calculateDamage(40), 0.0001f);
        assertEquals(300.0f, PlasmaGeneration.calculateDamage(240), 0.0001f);
        assertEquals(5.0f, PlasmaGeneration.calculateExplosionRadius(40), 0.0001f);
        assertEquals(30.0f, PlasmaGeneration.calculateExplosionRadius(240), 0.0001f);
    }

    @Test
    void referenceRuntimeConstantsStayAligned() {
        assertEquals(240, PlasmaGeneration.MAX_CHARGE_TICKS);
        assertEquals(40, PlasmaGeneration.CP_PER_SECOND);
        assertEquals(2.5, PlasmaGeneration.TRAVEL_SPEED, 0.0001);
        assertEquals(6, PlasmaGeneration.MAX_STAGE);
    }
}
