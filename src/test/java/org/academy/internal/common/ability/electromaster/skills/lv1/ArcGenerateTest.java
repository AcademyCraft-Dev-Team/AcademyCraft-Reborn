package org.academy.internal.common.ability.electromaster.skills.lv1;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArcGenerateTest {
    @Test
    void damageUsesReferenceBaseAndSharedPlayerMultiplier() {
        assertEquals(4.0f, ArcGenerate.getDamage(1.0f));
        assertEquals(6.0f, ArcGenerate.getDamage(1.5f));
        assertEquals(0.0f, ArcGenerate.getDamage(-1.0f));
    }
}
