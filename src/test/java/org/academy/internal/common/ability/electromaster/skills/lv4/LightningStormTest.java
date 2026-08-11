package org.academy.internal.common.ability.electromaster.skills.lv4;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LightningStormTest {
    @Test
    void damageUsesPlayerScaling() {
        assertEquals(10.0f, LightningStorm.Server.calculateDamage(100.0f, 1.0f, 1.0f), 0.0001f);
        assertEquals(20.0f, LightningStorm.Server.calculateDamage(100.0f, 1.5f, 1.5f), 0.0001f);
        assertEquals(0.0f, LightningStorm.Server.calculateDamage(0.0f, 0.0f, 1.0f), 0.0001f);
    }
}
