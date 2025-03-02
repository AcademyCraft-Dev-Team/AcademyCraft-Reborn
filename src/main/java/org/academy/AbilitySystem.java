package org.academy;

import org.academy.api.common.ability.AbilityCategory;

import java.util.HashMap;
import java.util.Map;

public final class AbilitySystem {
    public static final Map<String, AbilityCategory> ABILITY_CATEGORY_MAP = new HashMap<>();

    public static void init() {
        AbilitySystemServer.init();
    }

    /**
     * 会在 ServerLifecycleEvents.SERVER_STARTING 和 ClientLifecycleEvents.CLIENT_STARTED 的时候统一初始化，所以只需要在onInitialize的时候或之前注册即可
     */
    public static void registerAbilityCategory(final AbilityCategory abilityCategory) {
        ABILITY_CATEGORY_MAP.put(abilityCategory.name, abilityCategory);
    }
}