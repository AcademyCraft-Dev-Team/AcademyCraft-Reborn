package org.academy;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.common.world.level.storage.AcademyCraftWorldData;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class AbilitySystem {
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

        ServerLifecycleEvents.SERVER_STARTED.register((server) -> {
            AcademyCraft.executorService.scheduleAtFixedRate(() -> {
                if (server.isRunning()) {
                    // none
                }
            }, 0, 50, TimeUnit.MILLISECONDS);
        });
    }

    /**
     * 会在 ServerLifecycleEvents.SERVER_STARTING 和 ClientLifecycleEvents.CLIENT_STARTED 的时候统一初始化，所以只需要在onInitialize的时候或之前注册即可
     */
    public static void registerAbilityCategory(final AbilityCategory abilityCategory) {
        abilityCategoryMap.put(abilityCategory.name, abilityCategory);
    }

    public static void initPlayer(ServerPlayer player) {
        if (!AcademyCraft.academyCraftWorldData.getPlayers().containsKey(player.getUUID().toString())) {
            AcademyCraftWorldData.Player data = new AcademyCraftWorldData.Player();
            data.setLevel(0);

            MathUtil.WeightedRandom weightedRandom = new MathUtil.WeightedRandom();
            for (AbilityCategory abilityCategory : abilityCategoryMap.values()) {
                weightedRandom.addItem(abilityCategory.name, abilityCategory.probability);
            }

            data.setAbilityCategory(weightedRandom.getRandomItem());
            AcademyCraft.academyCraftWorldData.getPlayers().put(player.getUUID().toString(), data);
        }
    }
}