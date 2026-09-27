package org.academy.internal.common.ability.darkmatter.skills.lv4;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DarkmatterRepairTest {
    @Test
    void productiveCostAndTargetCountFollowMilestones() {
        assertEquals(1.0f, DarkmatterRepair.Server.matterCost(1.0f, 0), 0.0001f);
        assertEquals(0.8f, DarkmatterRepair.Server.matterCost(1.0f, 1), 0.0001f);
        assertEquals(1, DarkmatterRepair.Server.repairTargetCount(false, 0));
        assertEquals(2, DarkmatterRepair.Server.repairTargetCount(true, 0));
        assertEquals(2, DarkmatterRepair.Server.repairTargetCount(false, 3));
        assertEquals(3, DarkmatterRepair.Server.repairTargetCount(true, 3));
    }

    @Test
    void alphaAndBetaUseIndependentContinuousPower() {
        assertEquals(4.0f, DarkmatterRepair.Server.maximumAbsorption(1.0f, 2), 0.0001f);
        assertEquals(12.0f, DarkmatterRepair.Server.maximumAbsorption(5.0f, 3), 0.0001f);
        assertEquals(0.01875f, DarkmatterRepair.Server.repairFraction(1.0f, 2), 0.0001f);
        assertEquals(10.0f, DarkmatterRepair.Server.bodyHeal(1.0f, 2), 0.0001f);
        assertEquals(38, DarkmatterRepair.Server.effectReductionTicks(1.0f, 2));
    }

    @Test
    void healingAndCostInterpolateAcrossPhaseAllocation() {
        assertEquals(1.0f, DarkmatterRepair.Server.bodyHeal(0.0f, 0), 0.0001f);
        assertEquals(4.5f, DarkmatterRepair.Server.bodyHeal(0.5f, 0), 0.0001f);
        assertEquals(8.0f, DarkmatterRepair.Server.bodyHeal(1.0f, 0), 0.0001f);
        assertEquals(4.0f, DarkmatterRepair.Server.matterCost(0.0f, 0), 0.0001f);
        assertEquals(2.5f, DarkmatterRepair.Server.matterCost(0.5f, 0), 0.0001f);
        assertEquals(3.2f, DarkmatterRepair.Server.matterCost(0.0f, 1), 0.0001f);
        assertEquals(2.0f, DarkmatterRepair.Server.matterCost(0.5f, 1), 0.0001f);
    }

    @Test
    void healingAddsOnePercentOfMaximumHealthAfterOutputBonuses() {
        assertEquals(1.2f, DarkmatterRepair.Server.healingAmount(0, 0, 1, 20), 0.0001f);
        assertEquals(28.0f, DarkmatterRepair.Server.healingAmount(1, 0, 1, 2000), 0.0001f);
        assertEquals(20.2f, DarkmatterRepair.Server.healingAmount(1, 2, 2, 20), 0.0001f);
        assertEquals(50.0f, DarkmatterRepair.Server.healingAmount(1, 2, 2, 3000), 0.0001f);
    }

    @Test
    void thirdMilestoneRemovesOnlyEveryFifthProductivePulse() {
        for (var pulse = 1; pulse <= 9; pulse++) {
            assertEquals(pulse == 5,
                    DarkmatterRepair.Server.removesHarmfulEffect(pulse, 3));
        }
        assertTrue(DarkmatterRepair.Server.removesHarmfulEffect(10, 3));
        assertFalse(DarkmatterRepair.Server.removesHarmfulEffect(5, 2));
    }
}
