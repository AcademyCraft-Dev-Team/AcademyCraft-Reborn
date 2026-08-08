package org.academy.internal.server.ability;

import org.academy.api.common.ability.LearningHelper;

/**
 * Immutable level transition captured when an ability development task starts.
 */
public record LevelDevelopmentPlan(
        boolean initialDevelopment,
        int sourceLevel,
        int targetLevel,
        int energyCost
) {
    public static LevelDevelopmentPlan create(boolean initialDevelopment, int persistedLevel) {
        var sourceLevel = initialDevelopment ? 0 : persistedLevel;
        if (sourceLevel < 0 || sourceLevel >= 5) {
            throw new IllegalArgumentException("Ability level cannot be developed: " + sourceLevel);
        }
        return new LevelDevelopmentPlan(
                initialDevelopment,
                sourceLevel,
                sourceLevel + 1,
                LearningHelper.getEstimatedLevelUpConsumption(sourceLevel)
        );
    }

    /**
     * Keeps the persisted CP level consistent with whether the player owns an ability category.
     */
    public static int normalizeLevelForCategory(boolean level0Category, int persistedLevel) {
        return level0Category ? 0 : Math.max(1, persistedLevel);
    }
}
