package org.academy.internal.common.ability.meltdowner.skills.lv5;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DisintegrateTest {
    @Test
    void everyTargetUsesItsOwnHealthAndPlayerScaling() {
        assertEquals(4.0f, Disintegrate.Server.calculateDamage(20.0f, 1.0f), 0.0001f);
        assertEquals(6.0f, Disintegrate.Server.calculateDamage(20.0f, 1.5f), 0.0001f);
        assertEquals(0.0f, Disintegrate.Server.calculateDamage(-1.0f, 1.0f), 0.0001f);
    }
}
