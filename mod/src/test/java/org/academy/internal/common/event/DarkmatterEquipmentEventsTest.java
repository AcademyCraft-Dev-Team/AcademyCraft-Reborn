package org.academy.internal.common.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DarkmatterEquipmentEventsTest {
    @Test
    void armorReductionIsTenPercentPerProtectedPiece() {
        assertEquals(1.0f, DarkmatterEquipmentEvents.damageMultiplier(0), 0.0001f);
        assertEquals(0.7f, DarkmatterEquipmentEvents.damageMultiplier(3), 0.0001f);
        assertEquals(0.6f, DarkmatterEquipmentEvents.damageMultiplier(8), 0.0001f);
    }

    @Test
    void damageConversionHonorsLevelThresholdAndPartialMatter() {
        assertEquals(0.0f, DarkmatterEquipmentEvents.matterConversionTarget(4.99f, 5), 0.0001f);
        assertEquals(5.0f, DarkmatterEquipmentEvents.matterConversionTarget(10.0f, 5), 0.0001f);
        assertEquals(5.0f, DarkmatterEquipmentEvents.damageAfterMatterConversion(
                10.0f, 5, 100.0f), 0.0001f);
        assertEquals(8.0f, DarkmatterEquipmentEvents.damageAfterMatterConversion(
                10.0f, 5, 2.0f), 0.0001f);
        assertEquals(2.0f, DarkmatterEquipmentEvents.damageAfterMatterConversion(
                2.0f, 5, 100.0f), 0.0001f);
    }

    @Test
    void lethalAndInfiniteDamageStillConsumeOnlyTheAvailableMatter() {
        var lethal = DarkmatterEquipmentEvents.planMatterConversion(
                Float.MAX_VALUE, 5, 12.0f);
        assertEquals(12.0f, lethal.consumedMatter());
        assertEquals(Float.MAX_VALUE, lethal.remainingDamage());

        var genericKill = DarkmatterEquipmentEvents.planMatterConversion(
                Float.POSITIVE_INFINITY, 5, 12.0f);
        assertEquals(12.0f, genericKill.consumedMatter());
        assertEquals(Float.POSITIVE_INFINITY, genericKill.remainingDamage());
    }

    @Test
    void proficiencySelectsExactCarryLifetime() {
        assertEquals(12_000, DarkmatterEquipmentEvents.integrityLifetimeTicks(0));
        assertEquals(12_000, DarkmatterEquipmentEvents.integrityLifetimeTicks(1));
        assertEquals(14_400, DarkmatterEquipmentEvents.integrityLifetimeTicks(2));
        assertEquals(18_000, DarkmatterEquipmentEvents.integrityLifetimeTicks(3));
    }
}
