package org.academy.internal.common.ability.electromaster.skills.lv2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LightningNovaTest {
    @Test
    void wavefrontIncludesBothBoundariesAndRejectsInteriorAndExterior() {
        assertTrue(LightningNova.withinWavefront(6.5 * 6.5, 6.5 * 6.5, 64));
        assertTrue(LightningNova.withinWavefront(64, 6.5 * 6.5, 64));
        assertFalse(LightningNova.withinWavefront(6.49 * 6.49, 6.5 * 6.5, 64));
        assertFalse(LightningNova.withinWavefront(8.01 * 8.01, 6.5 * 6.5, 64));
    }

    @Test
    void damageUsesPlayerScaling() {
        assertEquals(4.0f, LightningNova.Server.calculateDamage(1.0f, 1.0f), 0.0001f);
        assertEquals(9.0f, LightningNova.Server.calculateDamage(1.5f, 1.5f), 0.0001f);
        assertEquals(0.0f, LightningNova.Server.calculateDamage(-1.0f, 1.0f), 0.0001f);
    }
}
