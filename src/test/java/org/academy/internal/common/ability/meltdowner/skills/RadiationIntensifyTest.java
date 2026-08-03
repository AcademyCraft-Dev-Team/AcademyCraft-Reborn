package org.academy.internal.common.ability.meltdowner.skills;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RadiationIntensifyTest {
    @Test
    void markedDamageUsesReferenceMultiplier() {
        assertEquals(10.0f, RadiationIntensify.amplifyDamage(10.0f, false));
        assertEquals(15.0f, RadiationIntensify.amplifyDamage(10.0f, true));
        assertEquals(0.0f, RadiationIntensify.amplifyDamage(Float.NaN, true));
    }

    @Test
    void markExpiryLastsTwoHundredTicksAndCannotOverflow() {
        assertEquals(1_200L, RadiationIntensify.markExpiry(1_000L));
        assertEquals(Long.MAX_VALUE, RadiationIntensify.markExpiry(Long.MAX_VALUE - 100L));
    }
}
