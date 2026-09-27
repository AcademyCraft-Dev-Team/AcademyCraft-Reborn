package org.academy.api.common.ability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillProficiencyProfileTest {
    @Test
    void costChannelsAreIndependentAndDiscrete() {
        var profile = SkillProficiencyProfile.builder()
                .costs(SkillProficiencyProfile.CostKind.CAST, 1.0f, 0.9f, 0.85f, 0.75f)
                .costs(SkillProficiencyProfile.CostKind.MAINTENANCE, 1.0f, 0.8f, 0.7f, 0.6f)
                .build();

        assertEquals(9.0f, profile.adjustCost(SkillProficiencyProfile.CostKind.CAST, 1, 10.0f));
        assertEquals(7.0f, profile.adjustCost(SkillProficiencyProfile.CostKind.MAINTENANCE, 2, 10.0f));
        assertEquals(10.0f, profile.adjustCost(SkillProficiencyProfile.CostKind.DYNAMIC, 3, 10.0f));
    }

    @Test
    void iterationOverrideOnlyAppliesAtConfiguredMilestones() {
        var profile = SkillProficiencyProfile.builder()
                .iterationTicks(10, 10, 10, 5)
                .build();

        assertEquals(10, profile.resolveIterationTicks(0, 40));
        assertEquals(10, profile.resolveIterationTicks(2, 40));
        assertEquals(5, profile.resolveIterationTicks(3, 40));
    }

    @Test
    void invalidCostDoesNotReachTheCpManager() {
        assertTrue(Float.isNaN(SkillProficiencyProfile.NONE.adjustCost(
                SkillProficiencyProfile.CostKind.CAST,
                0,
                Float.POSITIVE_INFINITY
        )));
    }
}
