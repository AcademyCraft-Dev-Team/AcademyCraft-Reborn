package org.academy.internal.common.ability.accelerator.skills.lv5;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BloodflowReverseTest {
    @Test
    void damageUsesFivePercentOfMaxHealthAndCappedConversionWithoutAddingMultipliers() {
        assertEquals(201.0f, BloodflowReverse.Server.calculateDamage(20.0f), 0.0001f);
        assertEquals(201.5f, BloodflowReverse.Server.calculateDamage(30.0f), 0.0001f);
        assertEquals(200.0f, BloodflowReverse.Server.calculateDamage(-1.0f), 0.0001f);
    }
}
