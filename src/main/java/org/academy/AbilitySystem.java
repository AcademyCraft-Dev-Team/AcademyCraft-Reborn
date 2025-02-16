package org.academy;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.AbilityCategoryIdentities;
import org.academy.api.common.ability.Skill;

import java.util.HashMap;
import java.util.Map;

public class AbilitySystem implements ModInitializer {
    public static final Map<String, AbilityCategory> abilityCategoryMap = new HashMap<>();

    @Override
    public void onInitialize() {
        for (AbilityCategory abilityCategory : abilityCategoryMap.values()) {
            abilityCategory.init();
            for (Skill skill : abilityCategory.skillList) {
                skill.init();
            }
        }
        ServerLifecycleEvents.SERVER_STARTING.register((server) -> {
            for (AbilityCategory abilityCategory : abilityCategoryMap.values()) {
                for (Skill skill : abilityCategory.skillList) {
                    skill.initServer();
                }
            }
        });
    }

    public static void registerAbilityCategory(final AbilityCategory abilityCategory) {
        abilityCategoryMap.put(abilityCategory.name, abilityCategory);
    }
}
