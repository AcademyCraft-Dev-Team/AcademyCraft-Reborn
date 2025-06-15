package org.academy.api.server.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftServer;
import org.academy.api.common.ability.*;
import org.academy.api.common.network.future.HandlePayload;
import org.academy.api.common.network.packet.S2CPacket;
import org.academy.api.common.util.MathUtil;
import org.academy.api.common.wireless.WirelessUser;
import org.academy.internal.common.ability.builtin.level0.Level0;
import org.academy.internal.common.world.level.block.entity.AbilityDeveloperBlockEntity;
import org.academy.internal.server.world.level.storage.WorldData;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.academy.api.common.ability.AbilitySystem.ABILITY_CATEGORY_MAP;
import static org.academy.api.common.ability.AbilitySystem.SKILL_MAP;
import static org.academy.api.server.ability.AbilitySystemServer.SyncType.COMPUTING_POWER;

public class AbilitySystemServer {
    public static final Map<UUID, Player> LIVE_PLAYER_MAP = new ConcurrentHashMap<>();
    private static final List<Runnable> RUNNABLE_LIST = new CopyOnWriteArrayList<>();
    private static Map<UUID, WorldData.Player> playerMap;
    public static volatile MinecraftServer minecraftServer;
    public static volatile ScheduledFuture<?> scheduledFuture;
    public static boolean paused;

    public static void init(final MinecraftServer server) {
        AcademyCraftServer.FUTURE_MANAGER_SERVER_INSTANCE.registerPayloadHandler(AbilitySystemServer.class);
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
                    } catch (Throwable e) {
                        AcademyCraft.LOGGER.error(e.getMessage());
                    }
                }, 0, 50, TimeUnit.MILLISECONDS
        );
    }

    @SuppressWarnings("resource")
    @HandlePayload
    public static AcquireCategoryPacket.Response handleAcquireCategory(AcquireCategoryPacket payload) {
        ServerPlayer player = null;
        Supplier<ServerGamePacketListenerImpl> supplier = payload.packetListenerSupplier;
        if (supplier != null) {
            PacketListener listener = supplier.get();
            if (listener instanceof ServerGamePacketListenerImpl gameListener) {
                player = gameListener.player;
            }
        }

        if (player == null) {
            AcademyCraft.LOGGER.error("Failed to get ServerPlayer from AcquireCategoryPacket listener supplier.");
            return new AcquireCategoryPacket.Response(Collections.singletonList("Error: Player context not found."));
        }

        BlockPos userPos = payload.userPos;
        if (player.position().distanceToSqr(Vec3.atCenterOf(userPos)) > 64.0) {
            return new AcquireCategoryPacket.Response(Collections.singletonList("Error: You are too far away."));
        }

        ServerLevel level = player.serverLevel();
        BlockEntity be = level.getBlockEntity(userPos);
        if (be instanceof AbilityDeveloperBlockEntity blockEntity) {
            List<String> outputList = new ArrayList<>();
            int energyStored = blockEntity.getEnergyStored();
            if (energyStored > 10_000) {
                blockEntity.setEnergyStored(energyStored - 10_000);
                MathUtil.WeightedRandom<AbilityCategory> weightedRandom = new MathUtil.WeightedRandom<>();
                for (AbilityCategory abilityCategory : ABILITY_CATEGORY_MAP.values()) {
                    if (abilityCategory != Level0.INSTANCE) {
                        weightedRandom.addItem(abilityCategory, abilityCategory.probability);
                    }
                }
                AbilityCategory abilityCategory = weightedRandom.getRandomItem();
                if (abilityCategory != null) {
                    setPlayerAbilityCategory(player.getUUID(), abilityCategory);
                    outputList.add("Learning complete. Type 'exit' to shut down, then reopen the screen to proceed.");
                } else {
                    outputList.add("Error: No ability category could be selected.");
                    AcademyCraft.LOGGER.error("WeightedRandom returned null for ability category selection.");
                }
            } else {
                outputList.add("Insufficient energy available.");
            }
            return new AcquireCategoryPacket.Response(outputList);
        }
        return new AcquireCategoryPacket.Response(Collections.singletonList("Error: Block is not a WirelessUser."));
    }

    @HandlePayload
    @SuppressWarnings("resource")
    public static LearnSkillPacket.Response handleLearnSkill(LearnSkillPacket payload) {
        ServerPlayer player = null;
        Supplier<ServerGamePacketListenerImpl> supplier = payload.packetListenerSupplier;
        if (supplier != null) {
            PacketListener listener = supplier.get();
            if (listener instanceof ServerGamePacketListenerImpl gameListener) {
                player = gameListener.player;
            }
        }

        if (player == null) {
            AcademyCraft.LOGGER.error("Failed to get ServerPlayer from LearnSkillPacket listener supplier.");
            return new LearnSkillPacket.Response(false);
        }

        BlockPos userPos = payload.userPos;
        if (player.position().distanceToSqr(Vec3.atCenterOf(userPos)) > 64.0) {
            return new LearnSkillPacket.Response(false);
        }

        ServerLevel level = player.serverLevel();
        String skillName = payload.skillName;
        BlockEntity be = level.getBlockEntity(userPos);
        if (be instanceof WirelessUser user) {
            if (SKILL_MAP.containsKey(skillName)) {
                Skill skill = SKILL_MAP.get(skillName);
                int energy = skill.energy;
                boolean depLearned = true;
                for (Skill dep : skill.dependencies) {
                    if (!getPlayerSkills(player.getUUID()).contains(dep.name)) {
                        depLearned = false;
                        break;
                    }
                }
                boolean learned = playerMap.get(player.getUUID()).getSkills().contains(skillName);
                boolean can = user.getEnergyStored() > energy && depLearned && !learned;
                if (can) {
                    user.extractEnergy(energy, false);
                    addPlayerSkill(player.getUUID(), skillName);
                }
                return new LearnSkillPacket.Response(can);
            }
        }
        return new LearnSkillPacket.Response(false);
    }

    public static void addAllPlayerSyncTask(final UUID uuid) {
        SyncType[] syncType = SyncType.values();
        for (SyncType type : syncType) {
            addPlayerSyncTask(uuid, type);
        }
    }

    public static AbilityCategory getPlayerAbilityCategory(UUID uuid) {
        return ABILITY_CATEGORY_MAP.get(playerMap.get(uuid).getAbilityCategory());
    }

    public static void setPlayerAbilityCategory(UUID uuid, AbilityCategory abilityCategory) {
        playerMap.get(uuid).setAbilityCategory(abilityCategory.name);
        addPlayerSyncTask(uuid, SyncType.ABILITY_CATEGORY);
    }

    public static HashSet<String> getPlayerSkills(UUID uuid) {
        return playerMap.get(uuid).getSkills();
    }

    public static void addPlayerSkill(UUID uuid, String skill) {
        playerMap.get(uuid).getSkills().add(skill);
        Skill skillI = SKILL_MAP.get(skill);
        if (skillI != null) {
            addPlayerSkillData(uuid, skill, skillI.getDefaultSkillData());
        }
        addPlayerSyncTask(uuid, SyncType.SKILLS);
    }

    public static void addPlayerSkillData(UUID uuid, String skill, WorldData.Player.SkillData skillData) {
        playerMap.get(uuid).getSkillData().put(skill, skillData);
    }

    public static void removePlayerSkill(UUID uuid, String skill) {
        playerMap.get(uuid).getSkills().remove(skill);
        addPlayerSyncTask(uuid, SyncType.SKILLS);
    }

    public static int getPlayerLevel(UUID uuid) {
        return playerMap.get(uuid).getLevel();
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public static <T extends WorldData.Player.SkillData> T getPlayerSkillData(UUID uuid, String skill) {
        playerMap.get(uuid).getSkillData().get(skill);
        return (T) playerMap.get(uuid).getSkillData().get(skill);
    }

    public static float getPlayerSkillExp(UUID uuid, String skill) {
        WorldData.Player.SkillData skillData = playerMap.get(uuid).getSkillData().get(skill);
        if (skillData == null) {
            return 0;
        } else {
            return skillData.exp;
        }
    }

    public static void setPlayerSkillExp(UUID uuid, String skill, float exp) {
        WorldData.Player.SkillData skillData = playerMap.get(uuid).getSkillData().get(skill);
        if (skillData != null) {
            skillData.exp = exp;
            Player player = LIVE_PLAYER_MAP.get(uuid);
            if (player != null) {
                player.connection.send(new S2CPacket(new ExpSyncPacket(skill, skillData.exp)));
            }
        }
    }

    public static void setPlayerLevel(UUID uuid, int level) {
        playerMap.get(uuid).setLevel(level);
    }

    public static float getPlayerComputingPower(UUID uuid) {
        return playerMap.get(uuid).getComputingPower();
    }

    public static void setPlayerComputingPower(UUID uuid, float power) {
        playerMap.get(uuid).setComputingPower(power);
        addPlayerSyncTask(uuid, COMPUTING_POWER);
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

    public static float getPlayerAdditionalComputingPower(UUID uuid) {
        if (!playerMap.containsKey(uuid)) {
            return -1;
        } else {
            Player livePlayer = LIVE_PLAYER_MAP.get(uuid);
            return livePlayer != null ? livePlayer.additionalComputingPower : 0;
        }
    }


    public static void setPlayerAdditionalComputingPower(UUID uuid, float power) {
        if (playerMap.containsKey(uuid)) {
            Player livePlayer = LIVE_PLAYER_MAP.get(uuid);
            if (livePlayer != null) {
                livePlayer.additionalComputingPower = power;
            }
        }
    }


    public static float getDamageMultiplier() {
        return AcademyCraftServer.abilityConfig.damageMultiplier;
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
        LEVEL,
        COMPUTING_POWER,
        MAX_COMPUTING_POWER,
        ABILITY_CATEGORY,
        SKILLS,
    }

    public static final class AbilitySystemServerThread {
        public static void tick() {
            if (paused) return;
            for (Runnable runnable : RUNNABLE_LIST) {
                runnable.run();
                RUNNABLE_LIST.remove(runnable);
            }
            for (Player player : LIVE_PLAYER_MAP.values()) {
                tickPlayer(player);
            }
        }

        public static void tickPlayer(Player player) {
            final Connection connection = player.connection;
            final UUID uuid = player.uuid;
            ConcurrentLinkedQueue<SyncType> syncQueue = player.syncQueue;
            boolean levelChanged = syncQueue.contains(SyncType.LEVEL);
            boolean currentComputingPowerChanged = syncQueue.contains(SyncType.COMPUTING_POWER);
            boolean maxComputingPowerChanged = syncQueue.contains(SyncType.MAX_COMPUTING_POWER);
            boolean abilityCategoryChanged = syncQueue.contains(SyncType.ABILITY_CATEGORY);
            boolean skillsChanged = syncQueue.contains(SyncType.SKILLS);
            PlayerSyncPacket packet = new PlayerSyncPacket(
                    levelChanged, getPlayerLevel(uuid),
                    currentComputingPowerChanged, getPlayerComputingPower(uuid),
                    maxComputingPowerChanged, getPlayerMaxComputingPower(uuid),
                    abilityCategoryChanged, getPlayerAbilityCategory(uuid).name,
                    skillsChanged, getPlayerSkills(uuid)
            );
            connection.send(new S2CPacket(packet));
            player.syncQueue.clear();

            final float currentComputingPower = getPlayerComputingPower(uuid);
            final float maxComputingPower = getPlayerMaxComputingPower(uuid);
            final float computingPowerRecoverySpeed = getPlayerComputingPowerRecoverySpeed(uuid);
            if (currentComputingPower < maxComputingPower) {
                setPlayerComputingPower(uuid, currentComputingPower + computingPowerRecoverySpeed);
            }

            AbilityCategory abilityCategory = getPlayerAbilityCategory(uuid);
            int learned = getPlayerSkills(uuid).size();
            int all = abilityCategory.skillList.size();
            float progress;
            if (all == 0) progress = 100.0f;
            else progress = (float) learned / all;
        }
    }

    public static final class MinecraftServerThread {
        public static void initPlayer(ServerPlayer player) {
            if (AcademyCraftServer.worldData == null) {
                return;
            }
            if (!playerMap.containsKey(player.getUUID())) {
                WorldData.Player data = new WorldData.Player();
                playerMap.put(player.getUUID(), data);
                setPlayerLevel(player.getUUID(), 0);
                setPlayerAbilityCategory(player.getUUID(), Level0.INSTANCE);
                AcademyCraft.LOGGER.debug("Finish init player.");
            } else {
                AcademyCraft.LOGGER.debug("Player already exists.");
            }
        }

        public static void tickMinecraftServerThread(final MinecraftServer server) {
            server.getPlayerList().getPlayers().forEach(serverPlayer -> {
                final UUID uuid = serverPlayer.getUUID();
                if (!LIVE_PLAYER_MAP.containsKey(uuid)) {
                    LIVE_PLAYER_MAP.put(uuid, new Player(
                                    uuid, playerMap.get(uuid), serverPlayer.connection.connection
                            )
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

    public static class Player {
        public final UUID uuid;
        public final WorldData.Player data;
        public final ConcurrentLinkedQueue<SyncType> syncQueue = new ConcurrentLinkedQueue<>();
        private final Connection connection;
        public float additionalComputingPower;

        public Player(final UUID uuid, final WorldData.Player data, final Connection connection) {
            this.uuid = uuid;
            this.data = data;
            this.connection = connection;
        }
    }

    public static void registerContext(ServerContext serverContext) {
        AcademyCraft.EVENT_BUS.register(serverContext);
        AcademyCraftServer.NETWORK_SYSTEM_SERVER_INSTANCE.registerPacketListener(serverContext);
    }

    public static void unregisterContext(ServerContext serverContext) {
        AcademyCraft.EVENT_BUS.unregister(serverContext);
        AcademyCraftServer.NETWORK_SYSTEM_SERVER_INSTANCE.unregisterPacketListener(serverContext);
    }
}