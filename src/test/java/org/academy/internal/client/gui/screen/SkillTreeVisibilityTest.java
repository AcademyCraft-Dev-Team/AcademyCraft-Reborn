package org.academy.internal.client.gui.screen;

import org.academy.api.common.ability.SkillScope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillTreeVisibilityTest {
    @Test
    void commonTreeKeepsLockedRootAndDependentSkillsVisible() {
        assertTrue(SkillTreeVisibility.shouldDisplay(SkillScope.COMMON, false, false, false));
        assertTrue(SkillTreeVisibility.shouldDisplay(SkillScope.COMMON, false, true, false));
    }

    @Test
    void categoryTreeStillHidesSkillsWhoseRequirementsAreNotMet() {
        assertFalse(SkillTreeVisibility.shouldDisplay(SkillScope.CATEGORY, false, false, false));
        assertFalse(SkillTreeVisibility.shouldDisplay(SkillScope.CATEGORY, false, true, false));
        assertTrue(SkillTreeVisibility.shouldDisplay(SkillScope.CATEGORY, false, true, true));
        assertTrue(SkillTreeVisibility.shouldDisplay(SkillScope.CATEGORY, true, false, false));
    }
}
