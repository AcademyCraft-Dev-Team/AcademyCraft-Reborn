package org.academy.api.common.ability;

import org.academy.api.common.registries.Registries;

import java.util.ArrayList;
import java.util.List;

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

    public static boolean isSkillAvailableForCategory(AbilityCategory category, Skill skill) {
        if (category == null || skill == null) return false;
        return switch (skill.getScope()) {
            case CATEGORY -> skill.getCategory() == category;
            case COMMON -> category.supportsCommonSkills();
        };
    }

    /**
     * Returns the complete server-authoritative skill set exposed by a category.
     */
    public static List<Skill> getAvailableSkillsForCategory(AbilityCategory category) {
        if (category == null) return List.of();

        var result = new ArrayList<Skill>();
        for (var skill : Registries.SKILLS) {
            if (isSkillAvailableForCategory(category, skill)) {
                result.add(skill);
            }
        }
        return List.copyOf(result);
    }
}
