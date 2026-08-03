package org.academy.internal.common.ability.meltdowner.skills.lv5;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChainFusionTest {
    @Test
    void chainDamageUsesPlayerScaling() {
        assertEquals(10.0f, ChainFusion.Server.calculateDamage(10.0f, 1.0f), 0.0001f);
        assertEquals(15.0f, ChainFusion.Server.calculateDamage(10.0f, 1.5f), 0.0001f);
        assertEquals(0.0f, ChainFusion.Server.calculateDamage(-1.0f, 1.0f), 0.0001f);
    }
}
