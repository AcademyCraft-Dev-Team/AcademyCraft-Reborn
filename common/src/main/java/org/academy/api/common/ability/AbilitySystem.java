package org.academy.api.common.ability;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AbilitySystem {
    public static final Map<String, AbilityCategory> ABILITY_CATEGORY_MAP = new ConcurrentHashMap<>();
    public static final Map<String, Skill> SKILL_MAP = new ConcurrentHashMap<>();

    /**
     * 在 onInitialize 的时候注册即可
     */
    public static void registerAbilityCategory(final AbilityCategory abilityCategory) {
        ABILITY_CATEGORY_MAP.put(abilityCategory.name, abilityCategory);
        for (Skill skill : abilityCategory.skillList) {
            SKILL_MAP.put(skill.name, skill);
        }
    }
}