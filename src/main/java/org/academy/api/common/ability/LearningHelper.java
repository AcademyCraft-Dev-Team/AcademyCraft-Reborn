package org.academy.api.common.ability;

/**
 * Judging utilities about ability development costs.
 * Mirrors the legacy {@code LearningHelper.getEstimatedConsumption},
 * where the estimated consumption equals {@code DeveloperType.CPS * action stimulations}.
 */
public final class LearningHelper {
    /**
     * Consumption per stimulation (IF), mirrors the legacy NORMAL developer type.
     */
    public static final int DEVELOPER_CPS = 700;

    private LearningHelper() {
    }

    /**
     * Estimated energy consumption for leveling up (or acquiring a category),
     * where level-up stimulation count equals {@code 5 * (level + 1)}.
     */
    public static int getEstimatedLevelUpConsumption(int currentLevel) {
        return DEVELOPER_CPS * 5 * (currentLevel + 1);
    }

    /**
     * Estimated energy consumption for developing a skill.
     */
    public static int getEstimatedSkillConsumption(Skill skill) {
        return skill.getEnergyCostToLearn();
    }
}
