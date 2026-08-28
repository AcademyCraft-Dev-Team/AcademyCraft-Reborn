package org.academy.internal.common.ability.aeromanip.skills.lv3;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AtmosphereShieldTest {
    @Test
    void everyShieldEffectUsesEightCompressedAirByDefault() {
        assertEquals(8.0f, AtmosphereShield.effectAirCost(8.0f));
        assertEquals(8.0f, AtmosphereShield.effectAirCost(Float.NaN));
        assertEquals(0.0f, AtmosphereShield.effectAirCost(-2.0f));
    }
}
