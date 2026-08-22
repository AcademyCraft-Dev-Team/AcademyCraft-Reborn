package org.academy.internal.common.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import org.academy.api.common.ability.DevelopmentSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbilityDevelopmentAccessTest {
    private static final DevelopmentSource TABLET =
            DevelopmentSource.tablet(InteractionHand.MAIN_HAND);
    private static final DevelopmentSource MACHINE =
            DevelopmentSource.block(BlockPos.ZERO);

    @Test
    void tabletOnlyLearnsLevelOneThroughThreeSkills() {
        assertFalse(AbilityDevelopmentAccess.canLearnSkill(TABLET, 0));
        assertTrue(AbilityDevelopmentAccess.canLearnSkill(TABLET, 1));
        assertTrue(AbilityDevelopmentAccess.canLearnSkill(TABLET, 2));
        assertTrue(AbilityDevelopmentAccess.canLearnSkill(TABLET, 3));
        assertFalse(AbilityDevelopmentAccess.canLearnSkill(TABLET, 4));
        assertFalse(AbilityDevelopmentAccess.canLearnSkill(TABLET, 5));
    }

    @Test
    void tabletOnlyDevelopsAbilityThroughLevelThree() {
        assertTrue(AbilityDevelopmentAccess.canDevelopAbilityLevel(TABLET, 1));
        assertTrue(AbilityDevelopmentAccess.canDevelopAbilityLevel(TABLET, 2));
        assertTrue(AbilityDevelopmentAccess.canDevelopAbilityLevel(TABLET, 3));
        assertFalse(AbilityDevelopmentAccess.canDevelopAbilityLevel(TABLET, 4));
        assertFalse(AbilityDevelopmentAccess.canDevelopAbilityLevel(TABLET, 5));
    }

    @Test
    void fullSizeMachineSupportsAllNormalDevelopmentLevels() {
        for (var level = 1; level <= 5; level++) {
            assertTrue(AbilityDevelopmentAccess.canLearnSkill(MACHINE, level));
            assertTrue(AbilityDevelopmentAccess.canDevelopAbilityLevel(MACHINE, level));
        }
    }
}
