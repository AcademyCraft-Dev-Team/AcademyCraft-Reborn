package org.academy;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.network.AcademyCraftNetworkResourceLocations;
import org.academy.api.common.network.FriendlyByteBufSerializers;
import org.academy.api.common.network.packet.ServerToClientPacket;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.server.world.level.storage.AcademyCraftWorldData;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.academy.AbilitySystem.ABILITY_CATEGORY_MAP;

public class AbilitySystemServer {
    public static Map<UUID, AcademyCraftWorldData.Player> playerMap;
    private static final List<Runnable> RUNNABLE_LIST = new CopyOnWriteArrayList<>();
    public static final List<UUID> UUID_LIST = new CopyOnWriteArrayList<>();
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
    }

    public static final class AbilitySystemServerThread {
        public static void tick() {
            if (running && !paused) {
                for (Runnable runnable : RUNNABLE_LIST) {
                    runnable.run();
                    RUNNABLE_LIST.remove(runnable);
                }
                for (UUID uuid : UUID_LIST) {
                    tickPlayer(uuid);
                }
            }
        }

        public static void tickPlayer(final UUID uuid) {
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
        public static void initPlayer(ServerPlayer player) {
            if (AcademyCraftServer.academyCraftWorldData == null) {
                return;
            }
            if (!AcademyCraftServer.academyCraftWorldData.getPlayers().containsKey(player.getUUID())) {
                AcademyCraftWorldData.Player data = new AcademyCraftWorldData.Player();
                data.setLevel(0);

                MathUtil.WeightedRandom weightedRandom = new MathUtil.WeightedRandom();
                for (AbilityCategory abilityCategory : ABILITY_CATEGORY_MAP.values()) {
                    weightedRandom.addItem(abilityCategory.name, abilityCategory.probability);
                }

                data.setAbilityCategory(weightedRandom.getRandomItem());
                AcademyCraftServer.academyCraftWorldData.getPlayers().put(player.getUUID(), data);
            }
            player.connection.send(new ServerToClientPacket(AcademyCraftNetworkResourceLocations.S2C_INIT_PACKET, FriendlyByteBufSerializers.ABILITY_CATEGORY_FRIENDLY_BYTE_BUF_SERIALIZER.serialize(new FriendlyByteBuf(Unpooled.buffer()),getAbilityCategory(player.getUUID()))));
        }

        public static void tickMinecraftServerThread(final MinecraftServer server) {
            running = server.isRunning();
            UUID_LIST.clear();
            server.getPlayerList().getPlayers().forEach(player -> {
                final UUID uuid = player.getUUID();
                UUID_LIST.add(uuid);
                player.connection.send(new ServerToClientPacket(AcademyCraftNetworkResourceLocations.S2C_SYNC_PACKET, getSyncFriendlyByteBuf(uuid)));
            });
        }
    }

    public static FriendlyByteBuf getSyncFriendlyByteBuf(UUID uuid) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        final float currentComputingPower = getPlayerComputingPower(uuid);
        final float maxComputingPower = getPlayerMaximumComputingPower(uuid);
        buffer.writeFloat(currentComputingPower);
        buffer.writeFloat(maxComputingPower);
        return buffer;
    }

    public static AbilityCategory getAbilityCategory(UUID uuid) {
        return ABILITY_CATEGORY_MAP.get(playerMap.get(uuid).getAbilityCategory());
    }

    public static List<String> getPlayerSkillList(UUID uuid) {
        return playerMap.get(uuid).getSkills();
    }

    public static int getPlayerLevel(UUID uuid) {
        return playerMap.get(uuid).getLevel();
    }

    public static void setPlayerLevel(UUID uuid, int level) {
        playerMap.get(uuid).setLevel(level);
    }

    public static float getPlayerComputingPower(UUID uuid) {
        return playerMap.get(uuid).getComputingPower();
    }

    public static void setPlayerComputingPower(UUID uuid, float power) {
        playerMap.get(uuid).setComputingPower(power);
    }

    public static float getPlayerMaximumComputingPower(UUID uuid) {
        return playerMap.get(uuid).getMaximumComputingPower();
    }

    public static void setPlayerMaximumComputingPower(UUID uuid, float power) {
        playerMap.get(uuid).setMaximumComputingPower(power);
    }

    public static float getPlayerComputingPowerRecoverySpeed(UUID uuid) {
        return playerMap.get(uuid).getComputingPowerRecoverySpeed();
    }

    public static void setPlayerComputingPowerRecoverySpeed(UUID uuid, float speed) {
        playerMap.get(uuid).setComputingPowerRecoverySpeed(speed);
    }

    public static float getDamageMultiplier() {
        return AcademyCraftServer.serverConfig.getAbility().getDamageMultiplier();
    }

    public static void addTask(Runnable runnable) {
        RUNNABLE_LIST.add(runnable);
    }
}