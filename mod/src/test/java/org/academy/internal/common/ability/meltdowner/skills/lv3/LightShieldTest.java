package org.academy.internal.common.ability.meltdowner.skills.lv3;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LightShieldTest {
    @Test
    void hostilePulseDamageUsesPlayerScaling() {
        assertEquals(3.0f, LightShield.calculateDamage(1.0f, 1.0f));
        assertEquals(6.75f, LightShield.calculateDamage(1.5f, 1.5f));
        assertEquals(0.0f, LightShield.calculateDamage(-1.0f, 1.0f));
    }

}
