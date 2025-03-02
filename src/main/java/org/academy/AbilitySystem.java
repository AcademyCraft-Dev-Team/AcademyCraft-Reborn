package org.academy;

import org.academy.api.common.ability.AbilityCategory;

import java.util.HashMap;
import java.util.Map;

public final class AbilitySystem {
    public static final Map<String, AbilityCategory> ABILITY_CATEGORY_MAP = new HashMap<>();

    /**
     * 在 onInitialize 的时候注册即可
     */
    public static void registerAbilityCategory(final AbilityCategory abilityCategory) {
        ABILITY_CATEGORY_MAP.put(abilityCategory.name, abilityCategory);
    }
}