package org.academy;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.network.AcademyCraftNetworkResourceLocations;
import org.academy.api.common.network.FriendlyByteBufIdentifiers;
import org.academy.api.common.util.MathUtil;
import org.academy.api.server.network.AcademyCraftRequestHandlersServer;
import org.academy.api.server.network.packet.S2CResponsePacket;
import org.academy.internal.server.world.level.storage.AcademyCraftWorldData;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.academy.AbilitySystem.ABILITY_CATEGORY_MAP;

public class AbilitySystemServer {
    public static void init() {
        ServerLifecycleEvents.SERVER_STARTING.register((server) -> {
            for (AbilityCategory abilityCategory : ABILITY_CATEGORY_MAP.values()) {
                abilityCategory.initServer(server);
                for (Skill skill : abilityCategory.skillList) {
                    skill.initServer(server);
                }
            }
        });

        ServerLifecycleEvents.SERVER_STARTED.register((server) -> {
            AcademyCraftWorldData academyCraftWorldData = AcademyCraftServer.academyCraftWorldData;
            Map<String, AcademyCraftWorldData.Player> playerMap = academyCraftWorldData.getPlayers();
            AcademyCraft.executorService.scheduleAtFixedRate(() -> {
                if (server.isRunning()) {
                    server.getPlayerList().getPlayers().forEach(player -> {
                        AcademyCraftWorldData.Player playerData = playerMap.get(player.getUUID().toString());
                        float currentComputingPower = playerData.getComputingPower();
                        float maxComputingPower = playerData.getMaximumComputingPower();
                        float computingPowerRecoverySpeed = playerData.getComputingPowerRecoverySpeed();
                        if (currentComputingPower < maxComputingPower) {
                            playerData.setComputingPower(currentComputingPower + computingPowerRecoverySpeed);
                        }
                    });
                }
            }, 0, 50, TimeUnit.MILLISECONDS);
            AcademyCraft.executorService.scheduleAtFixedRate(() -> {
                if (server.isRunning()) {
                    AcademyCraftWorldData.saveData();
                }
            }, 0, 10, TimeUnit.SECONDS);
        });
        AcademyCraftRequestHandlersServer.REQUEST_HANDLER_MAP.put(AcademyCraftNetworkResourceLocations.C2S_SYNC_REQUEST, (listener, packet) -> sync(listener.player));
    }

    public static void sync(ServerPlayer player) {
        AcademyCraftWorldData academyCraftWorldData = AcademyCraftServer.academyCraftWorldData;
        Map<String, AcademyCraftWorldData.Player> playerMap = academyCraftWorldData.getPlayers();
        AcademyCraftWorldData.Player playerData = playerMap.get(player.getUUID().toString());
        float currentComputingPower = playerData.getComputingPower();
        float maxComputingPower = playerData.getMaximumComputingPower();
        float computingPowerRecoverySpeed = playerData.getComputingPowerRecoverySpeed();
        player.connection.send(new S2CResponsePacket(FriendlyByteBufIdentifiers.LIST, AcademyCraftNetworkResourceLocations.S2C_SYNC_RESPONSE, List.of(FriendlyByteBufIdentifiers.FLOAT, currentComputingPower, maxComputingPower, computingPowerRecoverySpeed)));
    }

    public static void initPlayer(ServerPlayer player) {
        if (!AcademyCraftServer.academyCraftWorldData.getPlayers().containsKey(player.getUUID().toString())) {
            AcademyCraftWorldData.Player data = new AcademyCraftWorldData.Player();
            data.setLevel(0);

            MathUtil.WeightedRandom weightedRandom = new MathUtil.WeightedRandom();
            for (AbilityCategory abilityCategory : ABILITY_CATEGORY_MAP.values()) {
                weightedRandom.addItem(abilityCategory.name, abilityCategory.probability);
            }

            data.setAbilityCategory(weightedRandom.getRandomItem());
            AcademyCraftServer.academyCraftWorldData.getPlayers().put(player.getUUID().toString(), data);
        }
    }
}
