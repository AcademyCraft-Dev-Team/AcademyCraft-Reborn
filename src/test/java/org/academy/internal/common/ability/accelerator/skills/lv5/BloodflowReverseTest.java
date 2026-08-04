package org.academy.internal.common.ability.accelerator.skills.lv5;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BloodflowReverseTest {
    @Test
    void referenceDamageUsesCurrentHealthAndPlayerScaling() {
        assertEquals(20.0f, BloodflowReverse.Server.calculateDamage(20.0f, 1.0f), 0.0001f);
        assertEquals(30.0f, BloodflowReverse.Server.calculateDamage(20.0f, 1.5f), 0.0001f);
        assertEquals(0.0f, BloodflowReverse.Server.calculateDamage(-1.0f, 1.0f), 0.0001f);
        assertEquals(0.0f, BloodflowReverse.Server.calculateDamage(20.0f, -1.0f), 0.0001f);
    }
}
