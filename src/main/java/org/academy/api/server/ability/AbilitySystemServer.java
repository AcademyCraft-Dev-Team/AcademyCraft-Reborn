package org.academy.api.server.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftServer;
import org.academy.api.common.ability.*;
import org.academy.api.common.ability.pakcet.*;
import org.academy.api.common.registries.Registries;
import org.academy.api.common.util.MathUtil;
import org.academy.api.common.util.UncheckedUtil;
import org.academy.api.common.wireless.WirelessUser;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.skilldata.CommonSkillData;
import org.academy.internal.common.skilldata.SkillData;
import org.academy.internal.common.world.level.block.entity.AbilityDeveloperBlockEntity;
import org.academy.internal.server.ability.PlayerDataManager;
import org.academy.internal.server.world.level.storage.Player;
import org.jspecify.annotations.Nullable;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.future.annotation.HandleFuture;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public final class AbilitySystemServer {
    public static final Map<UUID, LivePlayer> LIVE_PLAYER_MAP = new ConcurrentHashMap<>();
    private static final List<Runnable> pendingTasks = new CopyOnWriteArrayList<>();
    private static PlayerDataManager playerDataManager;
    public static volatile MinecraftServer minecraftServer;
    public static volatile ScheduledFuture<?> scheduledFuture;

    public static void init(final MinecraftServer server, PlayerDataManager manager) {
        playerDataManager = manager;
        MisakaNetworkServer.FUTURE_MANAGER.registerFutureHandler(AbilitySystemServer.class);
        minecraftServer = server;

        for (var category : Registries.ABILITY_CATEGORIES) {
            category.initServer(server);
        }
        for (var skill : Registries.SKILLS) {
            skill.initServer(server);
        }

        var errorCount = new AtomicInteger(0);

        scheduledFuture = AcademyCraft.executorService.scheduleAtFixedRate(
                () -> {
                    try {
                        AbilitySystemTicker.tick();
                        errorCount.set(0);
                    } catch (Throwable e) {
                        var count = errorCount.incrementAndGet();
                        AcademyCraft.LOGGER.error(
                                "[AbilitySystemTicker] Consecutive error #{} - Timestamp: {}, Thread: {}",
                                count,
                                System.currentTimeMillis(),
                                Thread.currentThread().getName(),
                                e
                        );
                    }
                },
                0,
                50,
                TimeUnit.MILLISECONDS
        );
    }

    public static Player getPlayerData(UUID uuid) {
        return playerDataManager.getData(uuid);
    }

    @HandleFuture
    public static AcquireCategoryPacket.Response handleAcquireCategory(AcquireCategoryPacket payload) {
        var player = payload.getPacketListener().getPlayer();
        var userPos = BlockPos.of(payload.getUserPos());
        if (player.position().distanceToSqr(Vec3.atCenterOf(userPos)) > 64.0) {
            return new AcquireCategoryPacket.Response(Collections.singletonList("Error: You are too far away."));
        }

        var level = player.level();
        var be = level.getBlockEntity(userPos);
        if (be instanceof AbilityDeveloperBlockEntity blockEntity) {
            var outputList = new ArrayList<String>();
            var energyStored = blockEntity.getEnergyStored();
            if (energyStored > 10_000) {
                blockEntity.setEnergyStored(energyStored - 10_000);
                var weightedRandom = new MathUtil.WeightedRandom<AbilityCategory>();
                for (var category : Registries.ABILITY_CATEGORIES) {
                    if (category != AbilityCategories.LEVEL0.get()) {
                        weightedRandom.addItem(category, category.getProbability());
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
                outputList.add("Insufficient energy to develop ability.");
            }
            return new AcquireCategoryPacket.Response(outputList);
        }
        return new AcquireCategoryPacket.Response(Collections.singletonList("Error: Block is not an Ability Developer."));
    }

    @HandleFuture
    public static LearnSkillPacket.Response handleLearnSkill(LearnSkillPacket payload) {
        var player = payload.getPacketListener().getPlayer();
        var userPos = BlockPos.of(payload.getUserPos());
        if (player.position().distanceToSqr(Vec3.atCenterOf(userPos)) > 64.0) {
            return new LearnSkillPacket.Response(false);
        }

        var level = player.level();
        var skillKey = payload.getSkillName();
        var be = level.getBlockEntity(userPos);
        if (be instanceof WirelessUser user) {
            var skillReference = Registries.SKILLS.get(Identifier.parse(skillKey));
            if (skillReference.isPresent()) {
                var skill = skillReference.get().value();
                var energy = skill.getEnergyCostToLearn();
                var depLearned = true;
                for (var dep : skill.getDependencies()) {
                    if (!getPlayerData(player.getUUID()).isSkillLearned(dep.getKeyString())) {
                        depLearned = false;
                        break;
                    }
                }
                var learned = getPlayerData(player.getUUID()).isSkillLearned(skillKey);
                var canLearn = user.getEnergyStored() >= energy && depLearned && !learned;
                if (canLearn) {
                    user.extractEnergy(energy, false);
                    addPlayerSkill(player.getUUID(), skillKey);
                }
                return new LearnSkillPacket.Response(canLearn);
            }
        }
        return new LearnSkillPacket.Response(false);
    }

    public static AbilityCategory getPlayerAbilityCategory(UUID uuid) {
        var abilityCategory = getPlayerData(uuid).getAbilityCategory();
        AbilityCategory category;
        if (abilityCategory == null) {
            category = AbilityCategories.LEVEL0.get();
        } else {
            var categoryKey = Identifier.parse(abilityCategory);
            category = Registries.ABILITY_CATEGORIES.get(categoryKey).orElseThrow().value();
        }

        return category;
    }

    public static void setPlayerAbilityCategory(UUID uuid, AbilityCategory abilityCategory) {
        var categoryKey = Registries.ABILITY_CATEGORIES.getKey(abilityCategory);
        if (categoryKey != null) {
            getPlayerData(uuid).setAbilityCategory(categoryKey.toString());
            schedulePlayerSync(uuid, SyncTypes.ABILITY_CATEGORY);
        }
    }

    public static void addPlayerSkill(UUID uuid, String skillKey) {
        var playerData = getPlayerData(uuid);
        if (playerData.getSkillData().putIfAbsent(skillKey, new CommonSkillData(0)) == null) {
            var skill = Registries.SKILLS.get(Identifier.parse(skillKey));
            playerData.markDirty();
            schedulePlayerSync(uuid, SyncTypes.SKILL_DATA);
        }
    }

    public static void addPlayerSkillData(UUID uuid, String skillKey, SkillData skillData) {
        var playerData = getPlayerData(uuid);
        var oldValue = playerData.getSkillData().put(skillKey, skillData);
        if (!Objects.equals(oldValue, skillData)) {
            playerData.markDirty();
        }
    }

    public static void removePlayerSkill(UUID uuid, String skillKey) {
        var playerData = getPlayerData(uuid);
        if (playerData.getSkillData().remove(skillKey) != null) {
            playerData.markDirty();
            schedulePlayerSync(uuid, SyncTypes.SKILL_DATA);
        }
    }

    public static int getPlayerLevel(UUID uuid) {
        return getPlayerData(uuid).getLevel();
    }

    @Nullable
    public static <T extends SkillData> T getPlayerSkillData(UUID uuid, String skillKey) {
        return UncheckedUtil.uncheckedCast(getPlayerData(uuid).getSkillData().get(skillKey));
    }

    public static float getPlayerSkillExp(UUID uuid, String skillKey) {
        var skillData = getPlayerData(uuid).getSkillData().get(skillKey);
        if (skillData == null) {
            return 0;
        } else {
            return skillData.exp;
        }
    }

    public static void setPlayerLevel(UUID uuid, int level) {
        getPlayerData(uuid).setLevel(level);
    }

    public static float getPlayerComputingPower(UUID uuid) {
        return getPlayerData(uuid).getComputingPower();
    }

    public static void setPlayerComputingPower(UUID uuid, float power) {
        getPlayerData(uuid).setComputingPower(power);
        schedulePlayerSync(uuid, SyncTypes.COMPUTING_POWER);
    }

    public static float getPlayerMaxComputingPower(UUID uuid) {
        return getPlayerData(uuid).getMaxComputingPower();
    }

    public static void setPlayerMaxComputingPower(UUID uuid, float power) {
        getPlayerData(uuid).setMaxComputingPower(power);
        schedulePlayerSync(uuid, SyncTypes.MAX_COMPUTING_POWER);
    }

    public static float getPlayerComputingPowerRecoverySpeed(UUID uuid) {
        return getPlayerData(uuid).getComputingPowerRecoverySpeed();
    }

    public static void setPlayerComputingPowerRecoverySpeed(UUID uuid, float speed) {
        getPlayerData(uuid).setComputingPowerRecoverySpeed(speed);
    }

    public static float getPlayerAdditionalComputingPower(UUID uuid) {
        var playerData = getPlayerData(uuid);
        if (playerData == null) {
            return -1;
        } else {
            var livePlayer = LIVE_PLAYER_MAP.get(uuid);
            return livePlayer != null ? livePlayer.additionalComputingPower : 0;
        }
    }

    public static void setPlayerAdditionalComputingPower(UUID uuid, float power) {
        if (getPlayerData(uuid) != null) {
            var livePlayer = LIVE_PLAYER_MAP.get(uuid);
            if (livePlayer != null) {
                livePlayer.additionalComputingPower = power;
            }
        }
    }

    public static float getDamageMultiplier() {
        return AcademyCraftServer.abilityConfig == null
                ? 1
                : AcademyCraftServer.abilityConfig.damageMultiplier;
    }

    public static void schedulePlayerSync(final UUID uuid, final Identifier syncType) {
        if (LIVE_PLAYER_MAP.containsKey(uuid)) {
            LIVE_PLAYER_MAP.get(uuid).syncQueue.add(syncType);
        }
    }

    public static void addTask(Runnable runnable) {
        pendingTasks.add(runnable);
    }

    public static void onPlayerLogin(ServerPlayer player) {
        if (playerDataManager != null) {
            playerDataManager.onPlayerLogin(player);
        }
        var uuid = player.getUUID();
        LIVE_PLAYER_MAP.put(uuid, new LivePlayer(uuid, player.connection));
        schedulePlayerSync(uuid, SyncTypes.LEVEL);
        schedulePlayerSync(uuid, SyncTypes.ABILITY_CATEGORY);
        schedulePlayerSync(uuid, SyncTypes.COMPUTING_POWER);
        schedulePlayerSync(uuid, SyncTypes.MAX_COMPUTING_POWER);
        schedulePlayerSync(uuid, SyncTypes.SKILL_DATA);
    }

    public static final class AbilitySystemTicker {
        public static void tick() {
            if (minecraftServer.isPaused()) return;
            pendingTasks.forEach(Runnable::run);
            pendingTasks.clear();
            for (var player : LIVE_PLAYER_MAP.values()) {
                tickPlayer(player);
            }
        }

        public static void tickPlayer(LivePlayer player) {
            final var connection = player.connection;
            final var uuid = player.uuid;
            var syncQueue = player.syncQueue;
            var levelChanged = syncQueue.contains(SyncTypes.LEVEL);
            var currentComputingPowerChanged = syncQueue.contains(SyncTypes.COMPUTING_POWER);
            var maxComputingPowerChanged = syncQueue.contains(SyncTypes.MAX_COMPUTING_POWER);
            var abilityCategoryChanged = syncQueue.contains(SyncTypes.ABILITY_CATEGORY);
            var skillDataChanged = syncQueue.contains(SyncTypes.SKILL_DATA);

            if (levelChanged) {
                var packet = new SyncLevelPacket(getPlayerLevel(uuid));
                MisakaNetworkServer.sendPacket(connection, packet);
            }
            if (currentComputingPowerChanged) {
                var packet = new SyncComputingPowerPacket(getPlayerComputingPower(uuid));
                MisakaNetworkServer.sendPacket(connection, packet);
            }
            if (maxComputingPowerChanged) {
                var packet = new SyncMaxComputingPowerPacket(getPlayerMaxComputingPower(uuid));
                MisakaNetworkServer.sendPacket(connection, packet);
            }
            if (abilityCategoryChanged) {
                var packet = new SyncAbilityCategoryPacket(getPlayerAbilityCategory(uuid));
                MisakaNetworkServer.sendPacket(connection, packet);
            }
            if (skillDataChanged) {
                var skills = getPlayerData(uuid).getSkillData();
                var packet = new SyncSkillDataPacket(skills);
                MisakaNetworkServer.sendPacket(connection, packet);
            }
            player.syncQueue.clear();

            final var currentComputingPower = getPlayerComputingPower(uuid);
            final var maxComputingPower = getPlayerMaxComputingPower(uuid);
            final var computingPowerRecoverySpeed = getPlayerComputingPowerRecoverySpeed(uuid);
            if (currentComputingPower < maxComputingPower) {
                setPlayerComputingPower(uuid, Math.min(maxComputingPower, currentComputingPower + computingPowerRecoverySpeed));
            }
        }
    }

    @EventBusSubscriber
    public static final class ServerLifecycleHooks {
        @SubscribeEvent
        public static void tickMinecraftServerThread(ServerTickEvent.Pre event) {
            var server = event.getServer();
            var onlinePlayerUUIDs = server.getPlayerList().getPlayers().stream()
                    .map(Entity::getUUID)
                    .collect(Collectors.toSet());

            LIVE_PLAYER_MAP.keySet().removeIf(uuid -> !onlinePlayerUUIDs.contains(uuid));
        }
    }

    public static class LivePlayer {
        public final UUID uuid;
        public final Set<Identifier> syncQueue = ConcurrentHashMap.newKeySet();
        private final ServerGamePacketListenerImpl connection;
        public float additionalComputingPower;

        public LivePlayer(final UUID newUuid, final ServerGamePacketListenerImpl newConnection) {
            uuid = newUuid;
            connection = newConnection;
        }
    }

    public static void registerContext(ServerContext serverContext) {
        NeoForge.EVENT_BUS.register(serverContext);
        MisakaNetworkServer.NETWORK_MANAGER.registerPacketListener(serverContext);
    }

    public static void unregisterContext(ServerContext serverContext) {
        NeoForge.EVENT_BUS.unregister(serverContext);
        MisakaNetworkServer.NETWORK_MANAGER.unregisterPacketListener(serverContext);
    }
}