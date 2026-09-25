package org.academy.internal.server.ability;

import org.academy.api.common.ability.Skill;
import org.academy.api.common.data.AbilityData;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class PlayerCPManagerTest {
    @Test
    void automaticallyEnablesSkillDebugModeForNamedPlayers() {
        assertTrue(PlayerCPManager.isAutomaticSkillDebugPlayer("Dev"));
        assertTrue(PlayerCPManager.isAutomaticSkillDebugPlayer("Dusk_ark"));
    }

    @Test
    void doesNotAutomaticallyEnableOtherPlayers() {
        assertFalse(PlayerCPManager.isAutomaticSkillDebugPlayer("dev"));
        assertFalse(PlayerCPManager.isAutomaticSkillDebugPlayer("Player"));
    }

    @Test
    void acceleratesCpRecoveryFivefoldInSkillDebugMode() {
        assertEquals(5, PlayerCPManager.getCpIterationRate(true));
        assertEquals(1, PlayerCPManager.getCpIterationRate(false));
    }

    @Test
    void atomicReplacementCanReuseTheExistingPermanentReservation() {
        assertTrue(PlayerCPManager.isAtomicReplacementAffordable(10.0f, 50.0f, 60.0f));
        assertFalse(PlayerCPManager.isAtomicReplacementAffordable(10.0f, 50.0f, 60.01f));
    }

    @Test
    void atomicReplacementRejectsInvalidAmounts() {
        assertFalse(PlayerCPManager.isAtomicReplacementAffordable(Float.NaN, 0.0f, 0.0f));
        assertFalse(PlayerCPManager.isAtomicReplacementAffordable(10.0f, -1.0f, 5.0f));
        assertFalse(PlayerCPManager.isAtomicReplacementAffordable(10.0f, 0.0f, Float.POSITIVE_INFINITY));
    }

    @Test
    void atomicReplacementCanShrinkReservationsWhileAvailableCpIsNegative() {
        assertTrue(PlayerCPManager.isAtomicReplacementAffordable(-25.0f, 60.0f, 30.0f));
        assertTrue(PlayerCPManager.isAtomicReplacementAffordable(-25.0f, 60.0f, 0.0f));
        assertFalse(PlayerCPManager.isAtomicReplacementAffordable(-25.0f, 20.0f, 30.0f));
    }

    @Test
    void smallCpRecoveriesAccumulateBeforeSpIsConsumed() {
        var first = PlayerCPManager.planCpRecovery(4.0f, 0.0f, 10);
        assertEquals(4.0f, first.recoveredCp(), 0.0001f);
        assertEquals(4.0f, first.remainderCp(), 0.0001f);
        assertEquals(0, first.spCost());

        var second = PlayerCPManager.planCpRecovery(5.0f, first.remainderCp(), 10);
        assertEquals(5.0f, second.recoveredCp(), 0.0001f);
        assertEquals(9.0f, second.remainderCp(), 0.0001f);
        assertEquals(0, second.spCost());

        var third = PlayerCPManager.planCpRecovery(1.0f, second.remainderCp(), 10);
        assertEquals(1.0f, third.recoveredCp(), 0.0001f);
        assertEquals(0.0f, third.remainderCp(), 0.0001f);
        assertEquals(1, third.spCost());
    }

    @Test
    void finalSpOnlyRecoversCpUpToTheNextTenPointBoundary() {
        var plan = PlayerCPManager.planCpRecovery(20.0f, 8.0f, 1);
        assertEquals(2.0f, plan.recoveredCp(), 0.0001f);
        assertEquals(0.0f, plan.remainderCp(), 0.0001f);
        assertEquals(1, plan.spCost());
    }

    @Test
    void cpIterationRecoveryStopsAtZeroSp() {
        var plan = PlayerCPManager.planCpRecovery(0.25f, 9.0f, 0);
        assertEquals(0.0f, plan.recoveredCp(), 0.0001f);
        assertEquals(9.0f, plan.remainderCp(), 0.0001f);
        assertEquals(0, plan.spCost());
    }

    @Test
    void completeConsciousnessAnalysisLetsOneSpRecoverFourteenCp() {
        var plan = PlayerCPManager.planCpRecovery(20.0f, 0.0f, 1, 14.0f);
        assertEquals(14.0f, plan.recoveredCp(), 0.0001f);
        assertEquals(0.0f, plan.remainderCp(), 0.0001f);
        assertEquals(1, plan.spCost());
    }

    @Test
    void abilityLevelCpBonusesAreCumulative() {
        assertEquals(0.0f, PlayerCPManager.abilityLevelCpBonus(1));
        assertEquals(20.0f, PlayerCPManager.abilityLevelCpBonus(2));
        assertEquals(60.0f, PlayerCPManager.abilityLevelCpBonus(3));
        assertEquals(140.0f, PlayerCPManager.abilityLevelCpBonus(4));
        assertEquals(300.0f, PlayerCPManager.abilityLevelCpBonus(5));
    }

    @Test
    void cpBaselineCapsAndOverloadDurationMatchTheNewRules() {
        assertEquals(100.0f, PlayerCPManager.BASE_MAX_CP);
        assertEquals(300.0f, PlayerCPManager.MAX_SKILL_PROFICIENCY_CP_BONUS);
        assertEquals(200.0f, PlayerCPManager.MAX_CHALLENGE_CP_BONUS);
        assertEquals(200, PlayerCPManager.OVERLOAD_TICKS);
    }

    @Test
    void temporarilyDisablesStackLimitsForEverySkill() {
        assertFalse(Skill.STACK_LIMITS_ENABLED);
    }

    @Test
    void debugMaximumCpOverridesTheNaturallyCalculatedMaximum() {
        assertEquals(250.0f, PlayerCPManager.resolveEffectiveMaxCP(640.0f, 250.0f));
        assertEquals(640.0f, PlayerCPManager.resolveEffectiveMaxCP(640.0f, null));
        assertEquals(0.0f, PlayerCPManager.resolveEffectiveMaxCP(640.0f, Float.NaN));
    }

    @Test
    void combinedResourceCostRequiresBothCpAndMp() {
        assertTrue(PlayerCPManager.canAffordCombinedCost(20.0f, 40.0f, 20.0f, 40.0f));
        assertFalse(PlayerCPManager.canAffordCombinedCost(19.0f, 40.0f, 20.0f, 40.0f));
        assertFalse(PlayerCPManager.canAffordCombinedCost(20.0f, 39.0f, 20.0f, 40.0f));
    }

    @Test
    void combinedResourceCostRejectsInvalidValues() {
        assertFalse(PlayerCPManager.canAffordCombinedCost(
                Float.NaN, 40.0f, 20.0f, 40.0f));
        assertFalse(PlayerCPManager.canAffordCombinedCost(
                20.0f, 40.0f, -1.0f, 0.0f));
    }

    @Test
    void migratesRecalculatedCpGrowthIntoThePersistentMaximum() {
        assertEquals(400.0f, PlayerCPManager.initialPersistentMaxCp(100.0f, 300.0f));
        assertEquals(640.0f, PlayerCPManager.initialPersistentMaxCp(640.0f, 300.0f));
    }

    @Test
    void appliesOnlyNewGrowthToThePersistentMaximum() {
        assertEquals(420.0f, PlayerCPManager.applyDerivedMaxCpGrowth(400.0f, 300.0f, 320.0f));
        assertEquals(400.0f, PlayerCPManager.applyDerivedMaxCpGrowth(400.0f, 300.0f, 300.0f));
        assertEquals(420.0f, PlayerCPManager.applyDerivedMaxCpGrowth(420.0f, 320.0f, 20.0f));
    }

    @Test
    void calculationEfficiencyIsPointZeroFivePercentOfMaximumCp() {
        assertEquals(0.05f, PlayerCPManager.calculationEfficiency(100.0f));
        assertEquals(0.5f, PlayerCPManager.calculationEfficiency(1000.0f));
    }

    @Test
    void foodSpRecoveryLastsTenSecondsPerNutritionPoint() {
        assertEquals(0, PlayerCPManager.foodSpRecoveryDurationTicks(0));
        assertEquals(1_000, PlayerCPManager.foodSpRecoveryDurationTicks(5));
        assertEquals(1_600, PlayerCPManager.foodSpRecoveryDurationTicks(8));
    }

    @Test
    void everyStackAdvancesWithoutWaitingForEarlierCasts() {
        var occupations = new ArrayList<AbilityData.CpOccupationData>();
        for (var stack = 0; stack < 5; stack++) {
            occupations.add(new AbilityData.CpOccupationData(
                    10.0f,
                    10 + stack,
                    "academy:test_skill",
                    false
            ));
        }

        assertTrue(PlayerCPManager.advanceTimedOccupationIterations(occupations, 10));

        assertEquals(0, occupations.getFirst().getIterationTicks());
        for (var stack = 1; stack < occupations.size(); stack++) {
            assertEquals(stack, occupations.get(stack).getIterationTicks());
        }

        occupations.removeIf(AbilityData.CpOccupationData::isFree);
        assertEquals(4, occupations.size());
    }

    @Test
    void differentSkillQueuesAdvanceInParallel() {
        var occupations = new ArrayList<AbilityData.CpOccupationData>();
        occupations.add(new AbilityData.CpOccupationData(10.0f, 10, "academy:first", false));
        occupations.add(new AbilityData.CpOccupationData(10.0f, 10, "academy:first", false));
        occupations.add(new AbilityData.CpOccupationData(10.0f, 10, "academy:second", false));

        PlayerCPManager.advanceTimedOccupationIterations(occupations, 3);

        assertEquals(7, occupations.get(0).getIterationTicks());
        assertEquals(7, occupations.get(1).getIterationTicks());
        assertEquals(7, occupations.get(2).getIterationTicks());
    }

    @Test
    void dedicatedStackGroupsSeparateModesOfTheSameSkill() {
        var occupations = new ArrayList<AbilityData.CpOccupationData>();
        occupations.add(new AbilityData.CpOccupationData(
                10.0f, 5, "academy:vector_blast", false, "vector_blast:blast"));
        occupations.add(new AbilityData.CpOccupationData(
                10.0f, 5, "academy:vector_blast", false, "academy:vector_blast"));
        occupations.add(new AbilityData.CpOccupationData(
                10.0f, 0, "academy:vector_blast", true, "vector_blast:blast"));

        assertEquals(1, PlayerCPManager.countTimedStacks(occupations, "vector_blast:blast"));
        assertEquals(1, PlayerCPManager.countTimedStacks(occupations, "academy:vector_blast"));
    }
}
