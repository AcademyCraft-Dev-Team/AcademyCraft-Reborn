package org.academy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.network.AcademyCraftNetworkResourceLocations;
import org.academy.api.common.network.FriendlyByteBufIdentifiers;
import org.academy.api.common.util.MathUtil;
import org.academy.api.server.network.AcademyCraftRequestHandlersServer;
import org.academy.api.server.network.packet.S2CResponsePacket;
import org.academy.internal.server.world.level.storage.AcademyCraftWorldData;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.academy.AbilitySystem.ABILITY_CATEGORY_MAP;

public class AbilitySystemServer {
    public static Map<String, AcademyCraftWorldData.Player> playerMap;
    private static final List<Runnable> RUNNABLE_LIST = new CopyOnWriteArrayList<>();
    public static final List<String> UUID_LIST = new CopyOnWriteArrayList<>();
    @ApiStatus.Internal
    public static volatile boolean running;
    @ApiStatus.Internal
    public static volatile boolean paused = false;

    public static void init(final MinecraftServer server) {
        playerMap = AcademyCraftServer.academyCraftWorldData.getPlayers();

        for (AbilityCategory abilityCategory : ABILITY_CATEGORY_MAP.values()) {
            abilityCategory.initServer(server);
            for (Skill skill : abilityCategory.skillList) {
                skill.initServer(server);
            }
        }

        AcademyCraft.executorService.scheduleAtFixedRate(AbilitySystemServerThread::tick, 0, 50, TimeUnit.MILLISECONDS);
        AcademyCraftRequestHandlersServer.REQUEST_HANDLER_MAP.put(AcademyCraftNetworkResourceLocations.C2S_SYNC_REQUEST, (listener, packet) -> MinecraftServerThread.sync(listener.player));
    }

    public static final class AbilitySystemServerThread {
        public static void tick() {
            if (running && !paused) {
                for (Runnable runnable : RUNNABLE_LIST) {
                    runnable.run();
                    RUNNABLE_LIST.remove(runnable);
                }
                for (String uuid : UUID_LIST) {
                    tickPlayer(uuid);
                }
            }
        }

        public static void tickPlayer(final String uuid) {
            AcademyCraftWorldData.Player playerData = playerMap.get(uuid);
            float currentComputingPower = playerData.getComputingPower();
            float maxComputingPower = playerData.getMaximumComputingPower();
            float computingPowerRecoverySpeed = playerData.getComputingPowerRecoverySpeed();
            if (currentComputingPower < maxComputingPower) {
                playerData.setComputingPower(currentComputingPower + computingPowerRecoverySpeed);
            }
        }
    }

    public static final class MinecraftServerThread {
        public static void sync(final ServerPlayer player) {
            final String uuid = player.getStringUUID();
            final float currentComputingPower = getPlayerComputingPower(uuid);
            final float maxComputingPower = getPlayerMaximumComputingPower(uuid);
            final float computingPowerRecoverySpeed = getPlayerComputingPowerRecoverySpeed(uuid);
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

        public static void tickMinecraftServerThread(final MinecraftServer server) {
            running = server.isRunning();
            if (server instanceof IntegratedServer) {
                paused = Minecraft.getInstance().isPaused();
            }
            UUID_LIST.clear();
            server.getPlayerList().getPlayers().forEach(player -> UUID_LIST.add(player.getStringUUID()));
        }
    }

    public static int getPlayerLevel(String uuid) {
        return playerMap.get(uuid).getLevel();
    }

    public static void setPlayerLevel(String uuid, int level) {
        playerMap.get(uuid).setLevel(level);
    }

    public static float getPlayerComputingPower(String uuid) {
        return playerMap.get(uuid).getComputingPower();
    }

    public static void setPlayerComputingPower(String uuid, float power) {
        playerMap.get(uuid).setComputingPower(power);
    }

    public static float getPlayerMaximumComputingPower(String uuid) {
        return playerMap.get(uuid).getMaximumComputingPower();
    }

    public static void setPlayerMaximumComputingPower(String uuid, float power) {
        playerMap.get(uuid).setMaximumComputingPower(power);
    }

    public static float getPlayerComputingPowerRecoverySpeed(String uuid) {
        return playerMap.get(uuid).getComputingPowerRecoverySpeed();
    }

    public static void setPlayerComputingPowerRecoverySpeed(String uuid, float speed) {
        playerMap.get(uuid).setComputingPowerRecoverySpeed(speed);
    }

    public static float getDamageMultiplier() {
        return AcademyCraftServer.serverConfig.getAbility().getDamageMultiplier();
    }

    public static void addTask(Runnable runnable) {
        RUNNABLE_LIST.add(runnable);
    }
}