package org.academy.internal.common.ability.electromaster.skills.lv2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LightningNovaTest {
    @Test
    void damageUsesPlayerScaling() {
        assertEquals(4.0f, LightningNova.Server.calculateDamage(1.0f), 0.0001f);
        assertEquals(6.0f, LightningNova.Server.calculateDamage(1.5f), 0.0001f);
        assertEquals(0.0f, LightningNova.Server.calculateDamage(-1.0f), 0.0001f);
    }
}
