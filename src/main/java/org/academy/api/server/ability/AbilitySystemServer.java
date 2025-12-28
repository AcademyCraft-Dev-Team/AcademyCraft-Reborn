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
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftServer;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.AcquireCategoryPacket;
import org.academy.api.common.ability.LearnSkillPacket;
import org.academy.api.common.ability.SyncTypes;
import org.academy.api.common.ability.pakcet.SyncAbilityCategoryPacket;
import org.academy.api.common.ability.pakcet.SyncCPDataPacket;
import org.academy.api.common.ability.pakcet.SyncSkillDataPacket;
import org.academy.api.common.data.CPData;
import org.academy.api.common.registries.Registries;
import org.academy.api.common.util.MathUtil;
import org.academy.api.common.util.UncheckedUtil;
import org.academy.api.common.wireless.WirelessUser;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.skilldata.CommonSkillData;
import org.academy.internal.common.skilldata.SkillData;
import org.academy.internal.common.world.level.block.entity.AbilityDeveloperBlockEntity;
import org.academy.internal.server.ability.PlayerCPManager;
import org.academy.internal.server.ability.PlayerDataManager;
import org.academy.internal.server.world.level.storage.Player;
import org.jspecify.annotations.Nullable;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.future.annotation.HandleFuture;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public final class AbilitySystemServer {
    private static final Logger LOGGER = AcademyCraft.getLogger();

    public static final Map<UUID, LivePlayer> LIVE_PLAYER_MAP = new ConcurrentHashMap<>();
    private static final List<Runnable> pendingTasks = new CopyOnWriteArrayList<>();
    private static PlayerDataManager playerDataManager;
    public static volatile MinecraftServer minecraftServer;
    public static volatile ScheduledFuture<?> scheduledFuture;

    public static void init(final MinecraftServer server, PlayerDataManager dataManager) {
        playerDataManager = dataManager;
        MisakaNetworkServer.FUTURE_MANAGER.registerFutureHandler(AbilitySystemServer.class);
        minecraftServer = server;
        PlayerCPManager.init(dataManager);

        for (var category : Registries.ABILITY_CATEGORIES) {
            category.initServer(server);
        }
        for (var skill : Registries.SKILLS) {
            skill.initServer(server);
        }

        var errorCount = new AtomicInteger(0);

        scheduledFuture = AcademyCraft.EXECUTOR_SERVICE.scheduleAtFixedRate(
                () -> {
                    try {
                        AbilitySystemTicker.tick();
                        errorCount.set(0);
                    } catch (Throwable e) {
                        var count = errorCount.incrementAndGet();
                        LOGGER.error(
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
                    LOGGER.error("WeightedRandom returned null for ability category selection.");
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

    /**
     * 请求CP占用
     *
     * @param isPassive 是否被动占用（被动占用能使玩家进入个人现实过载状态）
     * @return 是否成功
     */
    public static boolean requestCPOccupation(UUID uuid, float amount, int iterationTicks, boolean isPassive) {
        return PlayerCPManager.requestCPOccupation(uuid, amount, iterationTicks, isPassive);
    }

    public static float getPlayerOccupiedCP(UUID uuid) {
        return PlayerCPManager.getOccupiedCP(uuid);
    }

    public static int getPlayerLevel(UUID uuid) {
        return PlayerCPManager.getLevel(uuid);
    }

    public static void setPlayerLevel(UUID uuid, int level) {
        PlayerCPManager.setLevel(uuid, level);
        schedulePlayerSync(uuid, SyncTypes.CP_DATA);
    }

    public static float getPlayerAvailableCP(UUID uuid) {
        return PlayerCPManager.getAvailableCP(uuid);
    }

    public static void setPlayerAvailableCP(UUID uuid, float availableCP) {
        PlayerCPManager.setAvailableCP(uuid, availableCP);
        schedulePlayerSync(uuid, SyncTypes.CP_DATA);
    }

    public static float getPlayerMaxCP(UUID uuid) {
        return PlayerCPManager.getMaxCP(uuid);
    }

    public static void setPlayerMaxCP(UUID uuid, float maxCP) {
        PlayerCPManager.setMaxCP(uuid, maxCP);
        schedulePlayerSync(uuid, SyncTypes.CP_DATA);
    }

    public static CPData.Status getPlayerStatus(UUID uuid) {
        return PlayerCPManager.getStatus(uuid);
    }

    public static void setPlayerStatus(UUID uuid, CPData.Status status) {
        PlayerCPManager.setStatus(uuid, status);
        schedulePlayerSync(uuid, SyncTypes.CP_DATA);
    }

    public static int getPlayerStateTimer(UUID uuid) {
        return PlayerCPManager.getStateTimer(uuid);
    }

    public static void setPlayerStateTimer(UUID uuid, int stateTimer) {
        PlayerCPManager.setStateTimer(uuid, stateTimer);
        schedulePlayerSync(uuid, SyncTypes.CP_DATA);
    }

    public static int getPlayerCurrSP(UUID uuid) {
        return PlayerCPManager.getCurrSP(uuid);
    }

    public static void setPlayerCurrSP(UUID uuid, int currSP) {
        PlayerCPManager.setCurrSP(uuid, currSP);
        schedulePlayerSync(uuid, SyncTypes.CP_DATA);
    }

    public static int getPlayerMaxSP(UUID uuid) {
        return PlayerCPManager.getMaxSP(uuid);
    }

    public static void setPlayerMaxSP(UUID uuid, int maxSP) {
        PlayerCPManager.setMaxSP(uuid, maxSP);
        schedulePlayerSync(uuid, SyncTypes.CP_DATA);
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
        schedulePlayerSync(uuid, SyncTypes.ABILITY_CATEGORY);
        schedulePlayerSync(uuid, SyncTypes.CP_DATA);
        schedulePlayerSync(uuid, SyncTypes.SKILL_DATA);

        PlayerCPManager.loadFromData(uuid, getPlayerData(uuid));
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

            var cpDataChanged = syncQueue.contains(SyncTypes.CP_DATA);
            var abilityCategoryChanged = syncQueue.contains(SyncTypes.ABILITY_CATEGORY);
            var skillDataChanged = syncQueue.contains(SyncTypes.SKILL_DATA);

            if (cpDataChanged) {
                var cpData = PlayerCPManager.getCPData(uuid);
                var packet = new SyncCPDataPacket(cpData);
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

            LIVE_PLAYER_MAP.forEach((uuid, livePlayer) -> {
                if (PlayerCPManager.tick(uuid)) {
                    schedulePlayerSync(uuid, SyncTypes.CP_DATA);
                }
            });
        }

        @SubscribeEvent
        public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            var uuid = event.getEntity().getUUID();
            PlayerCPManager.flushToData(uuid);
        }
    }

    public static class LivePlayer {
        public final UUID uuid;
        public final Set<Identifier> syncQueue = ConcurrentHashMap.newKeySet();
        private final ServerGamePacketListenerImpl connection;

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