package org.academy.api.common.ability;

import net.minecraft.util.Mth;
import org.academy.api.common.registries.Registries;

import java.util.ArrayList;
import java.util.List;

public final class LearningHelper {
    public static final int DEVELOPER_CPS = 700;

    private LearningHelper() {
    }

    public static int getEstimatedLevelUpConsumption(int currentLevel) {
        return DEVELOPER_CPS * 5 * (currentLevel + 1);
    }

    public static int getEstimatedSkillConsumption(Skill skill) {
        return skill.getEnergyCostToLearn();
    }

    public static boolean isSkillAvailableForCategory(AbilityCategory category, Skill skill) {
        return switch (skill.getScope()) {
            case CATEGORY -> skill.getCategory() == category;
            case COMMON -> category.supportsCommonSkills();
        };
    }

    public static float getAbilityExpRequirement(AbilityCategory category, int currentLevel) {
        var count = category.getSkills().stream()
                .filter(skill -> skill.getRecommendedLevel().getLevelCode() == currentLevel)
                .count();
        var base = Math.max(1L, count) * 1000.0f;
        return base * (currentLevel == 4 ? 1.333f : 0.666f);
    }

    public static float getAbilityProgress(AbilityCategory category, int currentLevel, float abilityExp) {
        var required = getAbilityExpRequirement(category, currentLevel);
        return required <= 0.0f ? 1.0f : Mth.clamp(abilityExp / required, 0.0f, 1.0f);
    }

    public static List<Skill> getAvailableSkillsForCategory(AbilityCategory category) {

        var result = new ArrayList<Skill>();
        for (var skill : Registries.SKILLS) {
            if (isSkillAvailableForCategory(category, skill)) {
                result.add(skill);
            }
        }
        return List.copyOf(result);
    }
}
