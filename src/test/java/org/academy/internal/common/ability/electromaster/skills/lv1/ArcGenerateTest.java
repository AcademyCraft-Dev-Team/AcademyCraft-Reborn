package org.academy.internal.common.ability.electromaster.skills.lv1;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArcGenerateTest {
    @Test
    void rangeIncreasesAtThe2000ProficiencyMilestone() {
        assertEquals(16, ArcGenerate.rangeForMilestone(0));
        assertEquals(16, ArcGenerate.rangeForMilestone(1));
        assertEquals(20, ArcGenerate.rangeForMilestone(2));
        assertEquals(20, ArcGenerate.rangeForMilestone(3));
    }

    @Test
    void damageUsesReferenceBaseAndSharedPlayerMultiplier() {
        assertEquals(8.0f, ArcGenerate.getDamage(1.0f, 1.0f));
        assertEquals(18.0f, ArcGenerate.getDamage(1.5f, 1.5f));
        assertEquals(0.0f, ArcGenerate.getDamage(-1.0f, 1.0f));
    }
}
