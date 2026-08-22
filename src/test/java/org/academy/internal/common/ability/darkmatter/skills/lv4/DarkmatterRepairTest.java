package org.academy.internal.common.ability.darkmatter.skills.lv4;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DarkmatterRepairTest {
    @Test
    void productiveCostAndTargetCountFollowMilestones() {
        assertEquals(1.0f, DarkmatterRepair.Server.matterCost(0), 0.0001f);
        assertEquals(0.8f, DarkmatterRepair.Server.matterCost(1), 0.0001f);
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
        assertEquals(1.25f, DarkmatterRepair.Server.bodyHeal(1.0f, 2), 0.0001f);
        assertEquals(38, DarkmatterRepair.Server.effectReductionTicks(1.0f, 2));
    }

    @Test
    void thirdMilestoneRemovesOnlyEveryFifthProductivePulse() {
        for (var pulse = 1; pulse <= 9; pulse++) {
            assertEquals(pulse == 5,
                    DarkmatterRepair.Server.removesHarmfulEffect(pulse, 3));
        }
        assertEquals(true, DarkmatterRepair.Server.removesHarmfulEffect(10, 3));
        assertEquals(false, DarkmatterRepair.Server.removesHarmfulEffect(5, 2));
    }
}
