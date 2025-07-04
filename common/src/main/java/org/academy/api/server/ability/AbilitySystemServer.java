package org.academy.api.server.ability;

import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
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
import java.util.stream.Collectors;

import static org.academy.api.common.ability.AbilitySystem.ABILITY_CATEGORY_MAP;
import static org.academy.api.common.ability.AbilitySystem.SKILL_MAP;
import static org.academy.api.server.ability.AbilitySystemServer.SyncType.COMPUTING_POWER;

public class AbilitySystemServer {
    public static final Map<UUID, Player> LIVE_PLAYER_MAP = new ConcurrentHashMap<>();
    private static final List<Runnable> pendingTasks = new CopyOnWriteArrayList<>();
    private static Map<UUID, WorldData.Player> playerMap;
    public static volatile MinecraftServer minecraftServer;
    public static volatile ScheduledFuture<?> scheduledFuture;
    public static boolean paused;

    public static void init(final MinecraftServer server) {
        AcademyCraftServer.SERVER_FUTURE_MANAGER.registerPayloadHandler(AbilitySystemServer.class);
        minecraftServer = server;
        playerMap = AcademyCraftServer.worldData.getPlayers();
        for (var abilityCategory : ABILITY_CATEGORY_MAP.values()) {
            abilityCategory.initServer(server);
            for (var skill : abilityCategory.skillList) {
                skill.initServer(server);
            }
        }
        scheduledFuture = AcademyCraft.executorService.scheduleAtFixedRate(
                () -> {
                    try {
                        AbilitySystemTicker.tick();
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
        var supplier = payload.packetListenerSupplier;
        if (supplier != null) {
            player = supplier.get().player;
        }

        if (player == null) {
            AcademyCraft.LOGGER.error("Failed to get ServerPlayer from AcquireCategoryPacket listener supplier.");
            return new AcquireCategoryPacket.Response(Collections.singletonList("Error: Player context not found."));
        }

        var userPos = payload.userPos;
        if (player.position().distanceToSqr(Vec3.atCenterOf(userPos)) > 64.0) {
            return new AcquireCategoryPacket.Response(Collections.singletonList("Error: You are too far away."));
        }

        var level = player.serverLevel();
        var be = level.getBlockEntity(userPos);
        if (be instanceof AbilityDeveloperBlockEntity blockEntity) {
            var outputList = new ArrayList<String>();
            var energyStored = blockEntity.getEnergyStored();
            if (energyStored > 10_000) {
                blockEntity.setEnergyStored(energyStored - 10_000);
                var weightedRandom = new MathUtil.WeightedRandom<AbilityCategory>();
                for (var abilityCategory : ABILITY_CATEGORY_MAP.values()) {
                    if (abilityCategory != Level0.INSTANCE) {
                        weightedRandom.addItem(abilityCategory, abilityCategory.probability);
                    }
                }
                var abilityCategory = weightedRandom.getRandomItem();
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
        var supplier = payload.packetListenerSupplier;
        if (supplier != null) {
            player = supplier.get().player;
        }

        if (player == null) {
            AcademyCraft.LOGGER.error("Failed to get ServerPlayer from LearnSkillPacket listener supplier.");
            return new LearnSkillPacket.Response(false);
        }

        var userPos = payload.userPos;
        if (player.position().distanceToSqr(Vec3.atCenterOf(userPos)) > 64.0) {
            return new LearnSkillPacket.Response(false);
        }

        var level = player.serverLevel();
        var skillName = payload.skillName;
        var be = level.getBlockEntity(userPos);
        if (be instanceof WirelessUser user) {
            if (SKILL_MAP.containsKey(skillName)) {
                var skill = SKILL_MAP.get(skillName);
                var energy = skill.energy;
                var depLearned = true;
                for (var dep : skill.dependencies) {
                    if (!getPlayerSkills(player.getUUID()).contains(dep.name)) {
                        depLearned = false;
                        break;
                    }
                }
                var learned = playerMap.get(player.getUUID()).getSkills().contains(skillName);
                var can = user.getEnergyStored() > energy && depLearned && !learned;
                if (can) {
                    user.extractEnergy(energy, false);
                    addPlayerSkill(player.getUUID(), skillName);
                }
                return new LearnSkillPacket.Response(can);
            }
        }
        return new LearnSkillPacket.Response(false);
    }

    public static void scheduleFullPlayerSync(final UUID uuid) {
        var syncType = SyncType.values();
        for (var type : syncType) {
            schedulePlayerSync(uuid, type);
        }
    }

    public static AbilityCategory getPlayerAbilityCategory(UUID uuid) {
        return ABILITY_CATEGORY_MAP.get(playerMap.get(uuid).getAbilityCategory());
    }

    public static void setPlayerAbilityCategory(UUID uuid, AbilityCategory abilityCategory) {
        playerMap.get(uuid).setAbilityCategory(abilityCategory.name);
        schedulePlayerSync(uuid, SyncType.ABILITY_CATEGORY);
    }

    public static HashSet<String> getPlayerSkills(UUID uuid) {
        return playerMap.get(uuid).getSkills();
    }

    public static void addPlayerSkill(UUID uuid, String skill) {
        playerMap.get(uuid).getSkills().add(skill);
        var skillI = SKILL_MAP.get(skill);
        if (skillI != null) {
            addPlayerSkillData(uuid, skill, skillI.getDefaultSkillData());
        }
        schedulePlayerSync(uuid, SyncType.SKILLS);
    }

    public static void addPlayerSkillData(UUID uuid, String skill, WorldData.Player.SkillData skillData) {
        playerMap.get(uuid).getSkillData().put(skill, skillData);
    }

    public static void removePlayerSkill(UUID uuid, String skill) {
        playerMap.get(uuid).getSkills().remove(skill);
        schedulePlayerSync(uuid, SyncType.SKILLS);
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
        var skillData = playerMap.get(uuid).getSkillData().get(skill);
        if (skillData == null) {
            return 0;
        } else {
            return skillData.exp;
        }
    }

    public static void setPlayerSkillExp(UUID uuid, String skill, float exp) {
        var skillData = playerMap.get(uuid).getSkillData().get(skill);
        if (skillData != null) {
            skillData.exp = exp;
            var player = LIVE_PLAYER_MAP.get(uuid);
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
        schedulePlayerSync(uuid, COMPUTING_POWER);
    }

    public static float getPlayerMaxComputingPower(UUID uuid) {
        return playerMap.get(uuid).getMaxComputingPower();
    }

    public static void setPlayerMaxComputingPower(UUID uuid, float power) {
        playerMap.get(uuid).setMaxComputingPower(power);
        schedulePlayerSync(uuid, SyncType.MAX_COMPUTING_POWER);
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
            var livePlayer = LIVE_PLAYER_MAP.get(uuid);
            return livePlayer != null ? livePlayer.additionalComputingPower : 0;
        }
    }


    public static void setPlayerAdditionalComputingPower(UUID uuid, float power) {
        if (playerMap.containsKey(uuid)) {
            var livePlayer = LIVE_PLAYER_MAP.get(uuid);
            if (livePlayer != null) {
                livePlayer.additionalComputingPower = power;
            }
        }
    }


    public static float getDamageMultiplier() {
        return AcademyCraftServer.abilityConfig.damageMultiplier;
    }

    public static void schedulePlayerSync(final UUID uuid, final SyncType syncType) {
        if (LIVE_PLAYER_MAP.containsKey(uuid)) {
            LIVE_PLAYER_MAP.get(uuid).syncQueue.add(syncType);
        }
    }

    public static void addTask(Runnable runnable) {
        pendingTasks.add(runnable);
    }

    public enum SyncType {
        LEVEL,
        COMPUTING_POWER,
        MAX_COMPUTING_POWER,
        ABILITY_CATEGORY,
        SKILLS,
    }

    public static final class AbilitySystemTicker {
        public static void tick() {
            if (paused) return;
            for (var runnable : pendingTasks) {
                runnable.run();
                pendingTasks.remove(runnable);
            }
            for (var player : LIVE_PLAYER_MAP.values()) {
                tickPlayer(player);
            }
        }

        public static void tickPlayer(Player player) {
            final var connection = player.connection;
            final var uuid = player.uuid;
            var syncQueue = player.syncQueue;
            var levelChanged = syncQueue.contains(SyncType.LEVEL);
            var currentComputingPowerChanged = syncQueue.contains(SyncType.COMPUTING_POWER);
            var maxComputingPowerChanged = syncQueue.contains(SyncType.MAX_COMPUTING_POWER);
            var abilityCategoryChanged = syncQueue.contains(SyncType.ABILITY_CATEGORY);
            var skillsChanged = syncQueue.contains(SyncType.SKILLS);
            var packet = new PlayerSyncPacket(
                    levelChanged, getPlayerLevel(uuid),
                    currentComputingPowerChanged, getPlayerComputingPower(uuid),
                    maxComputingPowerChanged, getPlayerMaxComputingPower(uuid),
                    abilityCategoryChanged, getPlayerAbilityCategory(uuid).name,
                    skillsChanged, getPlayerSkills(uuid)
            );
            connection.send(new S2CPacket(packet));
            player.syncQueue.clear();

            final var currentComputingPower = getPlayerComputingPower(uuid);
            final var maxComputingPower = getPlayerMaxComputingPower(uuid);
            final var computingPowerRecoverySpeed = getPlayerComputingPowerRecoverySpeed(uuid);
            if (currentComputingPower < maxComputingPower) {
                setPlayerComputingPower(uuid, currentComputingPower + computingPowerRecoverySpeed);
            }

            var abilityCategory = getPlayerAbilityCategory(uuid);
            var learned = getPlayerSkills(uuid).size();
            var all = abilityCategory.skillList.size();
            float progress;
            if (all == 0) progress = 100.0f;
            else progress = (float) learned / all;
        }
    }

    public static final class ServerLifecycleHooks {
        public static void initPlayer(ServerPlayer player) {
            if (AcademyCraftServer.worldData == null) {
                return;
            }
            if (!playerMap.containsKey(player.getUUID())) {
                var data = new WorldData.Player();
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
                final var uuid = serverPlayer.getUUID();
                if (!LIVE_PLAYER_MAP.containsKey(uuid)) {
                    LIVE_PLAYER_MAP.put(uuid, new Player(
                                    uuid, playerMap.get(uuid), serverPlayer.connection.connection
                            )
                    );
                    scheduleFullPlayerSync(uuid);
                }
            });
            var onlinePlayerUUIDs = server.getPlayerList().getPlayers().stream()
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

        public Player(final UUID newUuid, final WorldData.Player newData, final Connection newConnection) {
            uuid = newUuid;
            data = newData;
            connection = newConnection;
        }
    }

    public static void registerContext(ServerContext serverContext) {
        AcademyCraft.EVENT_BUS.register(serverContext);
        AcademyCraftServer.SERVER_NETWORK_MANAGER.registerPacketListener(serverContext);
    }

    public static void unregisterContext(ServerContext serverContext) {
        AcademyCraft.EVENT_BUS.unregister(serverContext);
        AcademyCraftServer.SERVER_NETWORK_MANAGER.unregisterPacketListener(serverContext);
    }
}