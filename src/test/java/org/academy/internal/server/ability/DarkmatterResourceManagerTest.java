package org.academy.internal.server.ability;

import org.academy.api.common.ability.AbilityResourceSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DarkmatterResourceManagerTest {
    private static final AbilityResourceSpec RESOURCE = new AbilityResourceSpec(0.20f, 2.0f);

    @Test
    void affordabilityIncludesTheOccupationBeingReplaced() {
        assertEquals(8.0f, DarkmatterResourceManager.affordableUnits(
                6.0f, 10.0f, 12.0f, RESOURCE));
        assertEquals(12.0f, DarkmatterResourceManager.affordableUnits(
                20.0f, 10.0f, 12.0f, RESOURCE));
    }

    @Test
    void invalidOrInsufficientBudgetsCannotPreserveMatter() {
        assertEquals(0.0f, DarkmatterResourceManager.affordableUnits(
                Float.NaN, 10.0f, 12.0f, RESOURCE));
        assertEquals(0.95f, DarkmatterResourceManager.affordableUnits(
                1.9f, 0.0f, 12.0f, RESOURCE));
    }

    @Test
    void baseCapacityUsesTheSquareOfAbilityLevel() {
        assertEquals(100.0f, DarkmatterResourceManager.baseCapacity(0));
        assertEquals(108.0f, DarkmatterResourceManager.baseCapacity(1));
        assertEquals(132.0f, DarkmatterResourceManager.baseCapacity(2));
        assertEquals(172.0f, DarkmatterResourceManager.baseCapacity(3));
        assertEquals(228.0f, DarkmatterResourceManager.baseCapacity(4));
        assertEquals(300.0f, DarkmatterResourceManager.baseCapacity(5));
    }

    @Test
    void creationUsesOnlyFreeCpAndCanAddFractionalMatter() {
        assertEquals(0.54f, DarkmatterResourceManager.creatableUnits(
                10.0f, 0.54f, RESOURCE), 0.0001f);
        assertEquals(0.24995f, DarkmatterResourceManager.creatableUnits(
                0.5f, 2.0f, RESOURCE), 0.0001f);
        assertEquals(0.0f, DarkmatterResourceManager.creatableUnits(
                0.0f, 2.0f, RESOURCE));
    }

    @Test
    void naturalMatterRecoversBesideCreatedMatter() {
        assertEquals(71.0f, DarkmatterResourceManager.recoverNaturalTotal(
                70.0f, 20.0f, 108.0f));
        assertEquals(118.0f, DarkmatterResourceManager.recoverNaturalTotal(
                118.0f, 10.0f, 108.0f));
        assertEquals(21.0f, DarkmatterResourceManager.recoverNaturalTotal(
                20.0f, 20.0f, 108.0f));
    }

    @Test
    void naturalRecoveryNeverTruncatesMatterWhenReservationLowersTheCapacity() {
        assertEquals(41.0f, DarkmatterResourceManager.recoverNaturalMatter(40.0f, 108.0f));
        assertEquals(108.0f, DarkmatterResourceManager.recoverNaturalMatter(108.0f, 108.0f));
        assertEquals(140.0f, DarkmatterResourceManager.recoverNaturalMatter(140.0f, 60.0f));
        assertEquals(40.0f, DarkmatterResourceManager.recoverNaturalMatter(40.0f, 0.0f));
    }

    @Test
    void consumptionUsesCreatedMatterFirstAndReleasesItsExactDebtShare() {
        var plan = DarkmatterResourceManager.planConsumption(10.0f, 20.0f, 36.0f, 15.0f);
        assertEquals(10.0f, plan.nextNaturalMatter());
        assertEquals(5.0f, plan.nextCreatedMatter());
        assertEquals(9.0f, plan.nextCreatedCpDebt());
        assertEquals(27.0f, plan.releasedCpDebt());
        assertEquals(15.0f, plan.consumedCreatedMatter());
        assertEquals(0.0f, plan.consumedNaturalMatter());
        assertEquals(15.0f, plan.totalConsumedMatter());
    }

    @Test
    void consumptionCrossesIntoNaturalPoolOnlyAfterCreatedPoolIsEmpty() {
        var plan = DarkmatterResourceManager.planConsumption(10.0f, 20.0f, 36.0f, 25.0f);
        assertEquals(5.0f, plan.nextNaturalMatter());
        assertEquals(0.0f, plan.nextCreatedMatter());
        assertEquals(0.0f, plan.nextCreatedCpDebt());
        assertEquals(36.0f, plan.releasedCpDebt());
        assertEquals(20.0f, plan.consumedCreatedMatter());
        assertEquals(5.0f, plan.consumedNaturalMatter());
        assertNull(DarkmatterResourceManager.planConsumption(1.0f, 2.0f, 4.0f, 4.0f));
    }
}
