package org.academy.internal.server.ability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LevelDevelopmentPlanTest {
    @Test
    void initialDevelopmentRepairsAnInconsistentPersistedLevel() {
        var plan = LevelDevelopmentPlan.create(true, 5);

        assertEquals(0, plan.sourceLevel());
        assertEquals(1, plan.targetLevel());
        assertEquals(3500, plan.energyCost());
    }

    @Test
    void ordinaryDevelopmentUsesTheCurrentLevel() {
        var plan = LevelDevelopmentPlan.create(false, 1);

        assertEquals(1, plan.sourceLevel());
        assertEquals(2, plan.targetLevel());
        assertEquals(7000, plan.energyCost());
    }

    @Test
    void ordinaryDevelopmentRejectsTheMaximumLevel() {
        assertThrows(IllegalArgumentException.class, () -> LevelDevelopmentPlan.create(false, 5));
    }

    @Test
    void level0CategoryAlwaysNormalizesToLevel0() {
        assertEquals(0, LevelDevelopmentPlan.normalizeLevelForCategory(true, 5));
    }

    @Test
    void acquiredCategoryIsAtLeastLevel1() {
        assertEquals(1, LevelDevelopmentPlan.normalizeLevelForCategory(false, 0));
        assertEquals(4, LevelDevelopmentPlan.normalizeLevelForCategory(false, 4));
    }
}
