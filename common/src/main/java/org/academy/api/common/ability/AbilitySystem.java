package org.academy.api.common.ability;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AbilitySystem {
    public static final Map<String, AbilityCategory> ABILITY_CATEGORY_MAP = new ConcurrentHashMap<>();
    public static final Map<String, Skill> SKILL_MAP = new ConcurrentHashMap<>();

    public static void init() {
        for (AbilityCategory category : ABILITY_CATEGORY_MAP.values()) {
            for (Skill skill : category.skillList) {
                skill.init();
            }
        }
    }

    public static void registerAbilityCategory(final AbilityCategory abilityCategory) {
        ABILITY_CATEGORY_MAP.put(abilityCategory.name, abilityCategory);
        for (Skill skill : abilityCategory.skillList) {
            SKILL_MAP.put(skill.name, skill);
        }
    }
}