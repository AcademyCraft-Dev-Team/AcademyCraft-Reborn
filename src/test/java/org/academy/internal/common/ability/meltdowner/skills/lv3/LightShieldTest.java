package org.academy.internal.common.ability.meltdowner.skills.lv3;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LightShieldTest {
    @Test
    void hostilePulseDamageUsesPlayerScaling() {
        assertEquals(3.0f, LightShield.calculateDamage(1.0f));
        assertEquals(4.5f, LightShield.calculateDamage(1.5f));
        assertEquals(0.0f, LightShield.calculateDamage(-1.0f));
    }

    @Test
    void heldIntervalsAndRadiusMatchReference() {
        assertEquals(2, LightShield.CP_INTERVAL_TICKS);
        assertEquals(4, LightShield.ATTACK_INTERVAL_TICKS);
        assertEquals(3.5, LightShield.ATTACK_RADIUS);
    }
}
