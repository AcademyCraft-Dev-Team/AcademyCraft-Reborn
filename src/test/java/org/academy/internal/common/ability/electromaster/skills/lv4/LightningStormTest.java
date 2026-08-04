package org.academy.internal.common.ability.electromaster.skills.lv4;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LightningStormTest {
    @Test
    void damageUsesPlayerScaling() {
        assertEquals(8.0f, LightningStorm.Server.calculateDamage(1.0f, 1.0f), 0.0001f);
        assertEquals(18.0f, LightningStorm.Server.calculateDamage(1.5f, 1.5f), 0.0001f);
        assertEquals(0.0f, LightningStorm.Server.calculateDamage(-1.0f, 1.0f), 0.0001f);
    }
}
