package org.academy;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.common.world.level.storage.AcademyCraftWorldData;

import java.util.HashMap;
import java.util.Map;

public class AbilitySystem {
    public static final Map<String, AbilityCategory> abilityCategoryMap = new HashMap<>();

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTING.register((server) -> {
            for (AbilityCategory abilityCategory : abilityCategoryMap.values()) {
                abilityCategory.initServer(server);
                for (Skill skill : abilityCategory.skillList) {
                    skill.initServer(server);
                }
            }
        });
    }

    public static void registerAbilityCategory(final AbilityCategory abilityCategory) {
        abilityCategoryMap.put(abilityCategory.name, abilityCategory);
    }

    public static void initPlayer(ServerPlayer player) {
        if (!AcademyCraft.academyCraftWorldData.getPlayers().containsKey(player.getUUID().toString())) {
            AcademyCraftWorldData.Player data = new AcademyCraftWorldData.Player();
            data.setLevel(0);

            MathUtil.WeightedRandom weightedRandom = new MathUtil.WeightedRandom();
            for (AbilityCategory abilityCategory : abilityCategoryMap.values()) {
                if (abilityCategory.customProbability) {
                    weightedRandom.addItem(abilityCategory.name, abilityCategory.probability);
                } else {
                    weightedRandom.addItem(abilityCategory.name, 1);
                }
            }

            data.setAbilityCategory(weightedRandom.getRandomItem());
            AcademyCraft.academyCraftWorldData.getPlayers().put(player.getUUID().toString(), data);
        }
    }
}
