package org.academy.api.server.ability;

import io.netty.buffer.Unpooled;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftServer;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.AbilitySystem;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.network.FriendlyByteBufSerializers;
import org.academy.api.common.network.NetworkResourceLocations;
import org.academy.api.common.network.packet.S2CPacket;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.server.world.level.storage.WorldData;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.academy.api.common.ability.AbilitySystem.ABILITY_CATEGORY_MAP;

public class AbilitySystemServer {
    public static Map<UUID, WorldData.Player<? extends WorldData.Player.SkillData>> playerMap;
    private static final List<Runnable> RUNNABLE_LIST = new CopyOnWriteArrayList<>();
    public static final Map<UUID, Player> LIVE_PLAYER_MAP = new ConcurrentHashMap<>();
    public static volatile MinecraftServer minecraftServer;
    public static volatile ScheduledFuture<?> scheduledFuture;

    public static void init(final MinecraftServer server) {
        minecraftServer = server;
        playerMap = AcademyCraftServer.worldData.getPlayers();
        for (AbilityCategory abilityCategory : ABILITY_CATEGORY_MAP.values()) {
            abilityCategory.initServer(server);
            for (Skill skill : abilityCategory.skillList) {
                skill.initServer(server);
            }
        }
        scheduledFuture = AcademyCraft.executorService.scheduleAtFixedRate(
                () -> {
                    try {
                        AbilitySystemServerThread.tick();
                    } catch (Exception e) {
                        AcademyCraft.LOGGER.error(e.getMessage());
                    }
                }, 0, 50, TimeUnit.MILLISECONDS
        );
    }

    public static final class AbilitySystemServerThread {
        public static void tick() {
            if (minecraftServer instanceof IntegratedServer integratedServer) {
                if (integratedServer.paused) return;
            }
            for (Runnable runnable : RUNNABLE_LIST) {
                runnable.run();
                RUNNABLE_LIST.remove(runnable);
            }
            for (Player player : LIVE_PLAYER_MAP.values()) {
                tickPlayer(player);
            }
            LIVE_PLAYER_MAP.values().forEach(player -> {
                final Consumer<Packet<?>> packetConsumer = player.packetConsumer;
                final UUID uuid = player.uuid;
                player.syncQueue.forEach(syncType -> {
                    switch (syncType) {
                        case COMPUTING_POWER -> packetConsumer.accept(
                                new S2CPacket(
                                        NetworkResourceLocations.S2C_COMPUTING_POWER_SYNC_PACKET,
                                        new FriendlyByteBuf(Unpooled.buffer()
                                                .writeFloat(getPlayerComputingPower(uuid))
                                        )
                                )
                        );
                        case ABILITY_CATEGORY -> packetConsumer.accept(
                                new S2CPacket(
                                        NetworkResourceLocations.S2C_ABILITY_CATEGORY_SYNC_PACKET,
                                        new FriendlyByteBuf(Unpooled.buffer())
                                                .writeUtf(getPlayerAbilityCategory(uuid).name)
                                )
                        );
                        case MAX_COMPUTING_POWER -> packetConsumer.accept(
                                new S2CPacket(
                                        NetworkResourceLocations.S2C_MAX_COMPUTING_POWER_SYNC_PACKET,
                                        new FriendlyByteBuf(Unpooled.buffer()
                                                .writeFloat(getPlayerMaxComputingPower(uuid))
                                        )
                                )
                        );
                        case SKILLS -> {
                            ArrayList<Skill> skillList = new ArrayList<>();
                            for (String string : getPlayerSkills(uuid)) {
                                skillList.add(AbilitySystem.SKILL_MAP.get(string));
                            }
                            FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.buffer());
                            FriendlyByteBufSerializers.SKILL_ARRAY_LIST_FRIENDLY_BYTE_BUF_SERIALIZER
                                    .serialize(friendlyByteBuf, skillList);
                            packetConsumer.accept(
                                    new S2CPacket(NetworkResourceLocations.S2C_SKILLS_SYC_PACKET, friendlyByteBuf)
                            );
                        }
                    }
                });
                player.syncQueue.clear();
            });
        }

        public static void tickPlayer(Player player) {
            final UUID uuid = player.uuid;
            final float currentComputingPower = getPlayerComputingPower(uuid);
            final float maxComputingPower = getPlayerMaxComputingPower(uuid);
            final float computingPowerRecoverySpeed = getPlayerComputingPowerRecoverySpeed(uuid);
            if (currentComputingPower < maxComputingPower) {
                setPlayerComputingPower(uuid, currentComputingPower + computingPowerRecoverySpeed);
            }
        }
    }

    public static final class MinecraftServerThread {
        public static void initPlayer(ServerPlayer player) {
            if (AcademyCraftServer.worldData == null) {
                return;
            }
            if (!playerMap.containsKey(player.getUUID())) {
                WorldData.Player<WorldData.Player.SkillData> data =
                        new WorldData.Player<>();
                data.setLevel(0);

                MathUtil.WeightedRandom weightedRandom = new MathUtil.WeightedRandom();
                for (AbilityCategory abilityCategory : ABILITY_CATEGORY_MAP.values()) {
                    weightedRandom.addItem(abilityCategory.name, abilityCategory.probability);
                }

                data.setAbilityCategory(weightedRandom.getRandomItem());
                playerMap.put(player.getUUID(), data);
            }
        }

        public static void tickMinecraftServerThread(final MinecraftServer server) {
            server.getPlayerList().getPlayers().forEach(serverPlayer -> {
                final UUID uuid = serverPlayer.getUUID();
                if (!LIVE_PLAYER_MAP.containsKey(uuid)) {
                    LIVE_PLAYER_MAP.put(uuid, new Player(
                            uuid, playerMap.get(uuid), packet -> serverPlayer.connection.send(packet))
                    );
                    addAllPlayerSyncTask(uuid);
                }
            });
            Set<UUID> onlinePlayerUUIDs = server.getPlayerList().getPlayers().stream()
                    .map(Entity::getUUID)
                    .collect(Collectors.toSet());

            LIVE_PLAYER_MAP.keySet().removeIf(uuid -> !onlinePlayerUUIDs.contains(uuid));
        }
    }

    public static void addAllPlayerSyncTask(final UUID uuid) {
        for (SyncType syncType : SyncType.values()) {
            addPlayerSyncTask(uuid, syncType);
        }
    }

    public static AbilityCategory getPlayerAbilityCategory(UUID uuid) {
        return ABILITY_CATEGORY_MAP.get(playerMap.get(uuid).getAbilityCategory());
    }

    public static void setPlayerAbilityCategory(UUID uuid, AbilityCategory abilityCategory) {
        playerMap.get(uuid).setAbilityCategory(abilityCategory.name);
        addPlayerSyncTask(uuid, SyncType.ABILITY_CATEGORY);
    }

    public static Set<String> getPlayerSkills(UUID uuid) {
        return playerMap.get(uuid).getSkills();
    }

    public static void addPlayerSkill(UUID uuid, String skill) {
        playerMap.get(uuid).getSkills().add(skill);
        addPlayerSyncTask(uuid, SyncType.SKILLS);
    }

    public static void removePlayerSkill(UUID uuid, String skill) {
        playerMap.get(uuid).getSkills().remove(skill);
        addPlayerSyncTask(uuid, SyncType.SKILLS);
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
        addPlayerSyncTask(uuid, SyncType.COMPUTING_POWER);
    }

    public static float getPlayerMaxComputingPower(UUID uuid) {
        return playerMap.get(uuid).getMaxComputingPower();
    }

    public static void setPlayerMaxComputingPower(UUID uuid, float power) {
        playerMap.get(uuid).setMaxComputingPower(power);
        addPlayerSyncTask(uuid, SyncType.MAX_COMPUTING_POWER);
    }

    public static float getPlayerComputingPowerRecoverySpeed(UUID uuid) {
        return playerMap.get(uuid).getComputingPowerRecoverySpeed();
    }

    public static void setPlayerComputingPowerRecoverySpeed(UUID uuid, float speed) {
        playerMap.get(uuid).setComputingPowerRecoverySpeed(speed);
    }

    /**
     *  You must check return.
     */
    public static float getPlayerAdditionalComputingPower(UUID uuid) {
        if (!playerMap.containsKey(uuid)) {
            return -1;
        } else {
            return LIVE_PLAYER_MAP.get(uuid).additionalComputingPower;
        }
    }

    public static void setPlayerAdditionalComputingPower(UUID uuid, float power) {
        if (playerMap.containsKey(uuid)) {
            LIVE_PLAYER_MAP.get(uuid).additionalComputingPower = power;
        }
    }

    public static float getDamageMultiplier() {
        return AcademyCraftServer.serverConfig.getAbility().getDamageMultiplier();
    }

    public static void addPlayerSyncTask(final UUID uuid, final SyncType syncType) {
        if (LIVE_PLAYER_MAP.containsKey(uuid)) {
            LIVE_PLAYER_MAP.get(uuid).syncQueue.add(syncType);
        }
    }

    public static void addTask(Runnable runnable) {
        RUNNABLE_LIST.add(runnable);
    }

    public enum SyncType {
        COMPUTING_POWER,
        MAX_COMPUTING_POWER,
        ABILITY_CATEGORY,
        SKILLS,
    }

    public static class Player {
        public final UUID uuid;
        public float additionalComputingPower;
        public final WorldData.Player<? extends WorldData.Player.SkillData> data;
        private final Consumer<Packet<?>> packetConsumer;
        public final ConcurrentLinkedQueue<SyncType> syncQueue = new ConcurrentLinkedQueue<>();

        public Player(final UUID uuid, final WorldData.Player<? extends WorldData.Player.SkillData> data, final Consumer<Packet<?>> packetConsumer) {
            this.uuid = uuid;
            this.data = data;
            this.packetConsumer = packetConsumer;
        }
    }
}