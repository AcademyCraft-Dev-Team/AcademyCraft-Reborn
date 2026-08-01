package org.academy.api.server.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.*;
import org.academy.api.common.ability.event.AbilityOverloadEvent;
import org.academy.api.common.ability.event.AbilityRecoveryEvent;
import org.academy.api.common.data.AbilityData;
import org.academy.api.common.registries.Registries;
import org.academy.api.common.util.MathUtil;
import org.academy.api.common.wireless.WirelessUser;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.server.ability.*;
import org.academy.internal.server.config.AbilityConfig;
import org.academy.internal.server.world.level.storage.Player;
import org.academy.internal.server.world.level.storage.WorldData;
import org.jetbrains.annotations.NotNull;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.future.annotation.HandleFuture;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class AbilitySystemServer {
    private static final Logger LOGGER = AcademyCraft.getLogger();
    private static final ConcurrentHashMap<UUID, DevelopData> DEVELOP_DATA_MAP = new ConcurrentHashMap<>();
    private static volatile boolean DEV_MODE = false;
    private final Map<UUID, Set<ServerContext>> activeContexts;
    private final SkillDataManager skillDataManager;
    private final PlayerDataManager playerDataManager;
    private final PlayerCPManager playerCPManager;
    private final SyncManager syncManager;
    private final ConcurrentHashMap<UUID, DevelopData> developDataMap = new ConcurrentHashMap<>();

    public AbilitySystemServer(MinecraftServerContext context, WorldData worldData, AbilityConfig abilityConfig) {
        syncManager = new SyncManager(context);

        playerDataManager = new PlayerDataManager(worldData, syncManager);
        SubsystemRegistry.registerSubsystem(playerDataManager, SyncTypes.ABILITY_CATEGORY);

        playerCPManager = new PlayerCPManager(playerDataManager, abilityConfig, syncManager);
        NeoForge.EVENT_BUS.register(playerCPManager);
        SubsystemRegistry.registerSubsystem(playerCPManager, SyncTypes.CP_DATA);

        skillDataManager = new SkillDataManager(playerDataManager, syncManager);
        SubsystemRegistry.registerSubsystem(skillDataManager, SyncTypes.SKILL_DATA);
        skillDataManager.setOnSkillLevelUp((uuid, levelsGained) -> {
            var currentMax = playerCPManager.getMaxCP(uuid);
            playerCPManager.setMaxCP(uuid, currentMax + (5.0f * levelsGained));
        });

        for (var category : Registries.ABILITY_CATEGORIES) {
            category.initServer(context);
        }

        for (var skill : Registries.SKILLS) {
            skill.initServer(context);
        }

        activeContexts = new ConcurrentHashMap<>();
        NeoForge.EVENT_BUS.register(this);

        MisakaNetworkServer.FUTURE_MANAGER.register(AbilitySystemServer.class);
        MisakaNetworkServer.NETWORK_MANAGER.register(AbilitySystemServer.class);
    }

    public static boolean isDevMode() {
        return DEV_MODE;
    }

    public static void setDevMode(boolean devMode) {
        if (DEV_MODE != devMode) {
            DEV_MODE = devMode;
            LOGGER.warn("DEV_MODE changed to {}", devMode);
        }
    }

    @SubscribePacket
    public static void handleStopDev(StopDevPacket packet) {
        var player = packet.getPacketListener().getPlayer();
        var devPos = packet.getUserPos();
        if (player.position().distanceToSqr(Vec3.atCenterOf(devPos)) > 64.0) {
            return;
        }

        var data = DEVELOP_DATA_MAP.get(player.getUUID());
        if (data != null && devPos.equals(data.getDeveloperPos())) {
            data.abort();
        }
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
                var serverContext = level.getServer();
                var instance = serverContext.getAcademyCraftServer().getAbilitySystemServer();

                for (var dep : skill.getDependencies()) {
                    if (!instance.getPlayerData(player.getUUID()).isSkillLearned(dep.getKeyString())) {
                        depLearned = false;
                        break;
                    }
                }
                var learned = instance.getPlayerData(player.getUUID()).isSkillLearned(skillKey);
                var canLearn = user.getEnergyStored() >= energy && depLearned && !learned;
                if (canLearn) {
                    user.extractEnergy(energy, false);
                    instance.addPlayerSkill(player, skillKey);
                }
                return new LearnSkillPacket.Response(canLearn);
            }
        }
        return new LearnSkillPacket.Response(false);
    }

    @HandleFuture
    public static StartSkillDevPacket.Response handleStartSkillDev(StartSkillDevPacket payload) {
        var player = payload.getPacketListener().getPlayer();
        var devPos = BlockPos.of(payload.getUserPos());
        if (player.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(devPos)) > 64.0) {
            return new StartSkillDevPacket.Response(false, "Too far away");
        }

        var level = player.level();
        var be = level.getBlockEntity(devPos);
        if (!(be instanceof org.academy.internal.common.world.level.block.entity.AbilityDeveloperBlockEntity developer)) {
            return new StartSkillDevPacket.Response(false, "Invalid developer");
        }

        var skillKey = payload.getSkillName();
        var skillRef = org.academy.api.common.registries.Registries.SKILLS.get(net.minecraft.resources.Identifier.parse(skillKey));
        if (skillRef.isEmpty()) {
            return new StartSkillDevPacket.Response(false, "Unknown skill");
        }
        var skill = skillRef.get().value();
        var server = level.getServer();
        var instance = server.getAcademyCraftServer().getAbilitySystemServer();
        var playerData = instance.getPlayerData(player.getUUID());

        // Dependency check
        for (var dep : skill.getDependencies()) {
            if (!playerData.isSkillLearned(dep.getKeyString())) {
                return new StartSkillDevPacket.Response(false, "Dependencies not met");
            }
        }

        // Already learned
        if (playerData.isSkillLearned(skillKey)) {
            return new StartSkillDevPacket.Response(false, "Already learned");
        }

        // Level check
        int playerLevel = instance.getPlayerLevel(player.getUUID());
        int recommendedLevel = skill.getRecommendedLevel().getLevelCode();
        if (playerLevel < recommendedLevel) {
            return new StartSkillDevPacket.Response(false, "Level too low");
        }

        // Energy check
        if (developer.getEnergyStored() < skill.getEnergyCostToLearn()) {
            return new StartSkillDevPacket.Response(false, "Insufficient energy");
        }

        // DevCondition check
        for (var cond : skill.getDevConditions()) {
            if (!cond.accepts(player, developer)) {
                return new StartSkillDevPacket.Response(false, cond.getHintText());
            }
        }

        // Start development
        var developData = getDevelopData(player.getUUID());
        developData.start(new DevelopAction() {
            @Override
            public int getTotalTicks() {
                return 200; // 10 seconds at 20 ticks/sec
            }

            @Override
            public void onComplete(ServerPlayer sp, org.academy.api.common.wireless.WirelessUser dev) {
                developer.setEnergyStored(developer.getEnergyStored() - skill.getEnergyCostToLearn());
                instance.addPlayerSkill(sp, skillKey);
            }
        }, devPos);

        return new StartSkillDevPacket.Response(true, "Started");
    }

    @HandleFuture
    public static StartLevelDevPacket.Response handleStartLevelDev(StartLevelDevPacket payload) {
        var player = payload.getPacketListener().getPlayer();
        var devPos = BlockPos.of(payload.getUserPos());
        if (player.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(devPos)) > 64.0) {
            return new StartLevelDevPacket.Response(false, "Too far away");
        }

        var level = player.level();
        var be = level.getBlockEntity(devPos);
        if (!(be instanceof org.academy.internal.common.world.level.block.entity.AbilityDeveloperBlockEntity developer)) {
            return new StartLevelDevPacket.Response(false, "Invalid developer");
        }

        var server = level.getServer();
        var instance = server.getAcademyCraftServer().getAbilitySystemServer();
        var currentLevel = instance.getPlayerLevel(player.getUUID());
        if (currentLevel >= 5) {
            return new StartLevelDevPacket.Response(false, "Already max level");
        }

        var hasCategory = instance.getPlayerAbilityCategory(player.getUUID()) != AbilityCategories.LEVEL0.get();
        if (hasCategory && !instance.canPlayerLevelUp(player.getUUID())) {
            return new StartLevelDevPacket.Response(false, "Ability exp not full");
        }

        var cost = LearningHelper.getEstimatedLevelUpConsumption(currentLevel);
        if (developer.getEnergyStored() < cost) {
            return new StartLevelDevPacket.Response(false, "Insufficient energy");
        }

        var developData = getDevelopData(player.getUUID());
        developData.start(new DevelopAction() {
            @Override
            public int getTotalTicks() {
                return 400; // 20 seconds
            }

            @Override
            public void onComplete(ServerPlayer sp, org.academy.api.common.wireless.WirelessUser dev) {
                developer.setEnergyStored(developer.getEnergyStored() - cost);
                if (instance.getPlayerAbilityCategory(sp.getUUID()) == AbilityCategories.LEVEL0.get()) {
                    var weightedRandom = new MathUtil.WeightedRandom<AbilityCategory>();
                    for (var category : Registries.ABILITY_CATEGORIES) {
                        if (category != AbilityCategories.LEVEL0.get()) {
                            weightedRandom.addItem(category, category.getProbability());
                        }
                    }
                    var abilityCategory = weightedRandom.getRandomItem();
                    if (abilityCategory != null) {
                        instance.setPlayerAbilityCategory(sp.getUUID(), abilityCategory);
                    } else {
                        LOGGER.error("WeightedRandom returned null for ability category selection.");
                    }
                } else {
                    instance.setPlayerLevel(sp.getUUID(), currentLevel + 1);
                    var levels = org.academy.api.common.ability.AbilityLevel.values();
                    if (currentLevel + 1 < levels.length) {
                        instance.setPlayerMaxCP(sp.getUUID(), levels[currentLevel + 1].getBasicCP());
                    }
                }
            }
        }, devPos);

        return new StartLevelDevPacket.Response(true, "Started");
    }

    public static void registerContext(ServerContext serverContext) {
        var player = serverContext.player;
        if (player == null) return;

        var instance = getSystem(player);
        instance.activeContexts.computeIfAbsent(player.getUUID(), _ -> ConcurrentHashMap.newKeySet())
                .add(serverContext);

        NeoForge.EVENT_BUS.register(serverContext);
        MisakaNetworkServer.NETWORK_MANAGER.register(serverContext);
    }

    public static void unregisterContext(ServerContext serverContext) {
        var player = serverContext.player;
        if (player == null) return;

        var instance = getSystem(player);
        var contexts = instance.activeContexts.get(player.getUUID());
        if (contexts == null) return;

        contexts.remove(serverContext);
        if (contexts.isEmpty()) instance.activeContexts.remove(player.getUUID());

        NeoForge.EVENT_BUS.unregister(serverContext);
        MisakaNetworkServer.NETWORK_MANAGER.unregister(serverContext);
    }

    public static AbilitySystemServer getSystem(Entity entity) {
        if (entity.level() instanceof ServerLevel serverLevel) {
            return serverLevel.getServer()
                    .getAcademyCraftServer()
                    .getAbilitySystemServer();
        }
        throw new IllegalStateException("Entity is not in a ServerLevel");
    }

    public static DevelopData getDevelopData(UUID uuid) {
        return DEVELOP_DATA_MAP.computeIfAbsent(uuid, DevelopData::new);
    }

    public static void tickDevelopments(net.minecraft.server.MinecraftServer server) {
        for (var entry : DEVELOP_DATA_MAP.entrySet()) {
            var data = entry.getValue();
            if (!data.isDeveloping()) continue;
            var player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;
            data.tick(player);
        }
    }

    public static float getSPReductionRate(LivingEntity entity) {
        return entity.getData(AttachmentTypes.SP_REDUCTION_RATE);
    }

    public static void setSPReductionRate(LivingEntity entity, float rate) {
        var clamped = Mth.clamp(rate, 0.0f, 1.0f);
        if (Float.compare(entity.getData(AttachmentTypes.SP_REDUCTION_RATE), clamped) != 0) {
            entity.setData(AttachmentTypes.SP_REDUCTION_RATE, clamped);
        }
    }

    public void schedulePlayerSync(final UUID uuid, final Identifier syncType) {
        syncManager.schedulePlayerSync(uuid, syncType);
    }

    public void onPlayerLogin(ServerPlayer player) {
        syncManager.onPlayerLogin(player);
        if (playerDataManager != null) {
            playerDataManager.onPlayerLogin(player);
        }
        for (var sub : SubsystemRegistry.getSubsystems()) {
            sub.onPlayerLogin(player);
        }
    }

    public void onPlayerLogout(ServerPlayer player) {
        syncManager.onPlayerLogout(player);
        for (var sub : SubsystemRegistry.getSubsystems()) {
            sub.onPlayerLogout(player);
        }

        var contexts = activeContexts.remove(player.getUUID());
        if (contexts == null) return;
        contexts.forEach(NeoForge.EVENT_BUS::unregister);

        contexts.forEach(ctx -> {
            NeoForge.EVENT_BUS.unregister(ctx);
            MisakaNetworkServer.NETWORK_MANAGER.unregister(ctx);
        });
    }

    public SyncManager getSyncManager() {
        return syncManager;
    }

    public Player getPlayerData(UUID uuid) {
        return playerDataManager.getData(uuid);
    }

    @SubscribeEvent
    public void onPlayerOverload(AbilityOverloadEvent event) {
        var uuid = event.getEntity().getUUID();
        var contexts = activeContexts.get(uuid);

        if (contexts != null) {
            var copy = List.copyOf(contexts);
            copy.forEach(ServerContext::unregister);
            LOGGER.info("Player {} overloaded: Force terminated {} contexts.", event.getEntity().getName().getString(), copy.size());
        }
    }

    @SubscribeEvent
    public void onOverloadRecovered(AbilityRecoveryEvent event) {
        var player = event.getEntity();
        var uuid = player.getUUID();
        var playerData = getPlayerData(uuid);
        if (playerData == null) return;

        // 恢复所有已启用的技能占用
        playerData.getSkillDataMap().forEach((skillId, data) -> {
            if (!data.isEnabled()) return;
            Registries.SKILLS.get(Identifier.parse(skillId)).ifPresent(skillRef -> {
                var skill = skillRef.value();
                var level = getPlayerSkillLevel(uuid, skillId);
                if (skill.getMaintenanceCost(level) <= 0) {
                    tryPermanentOccupation(uuid, skill.getMaintenanceCost(level), skill);
                }
            });
        });
        LOGGER.info("player {} recovered from overload.", player.getName().getString());
    }

    public void onServerStopping() {
        NeoForge.EVENT_BUS.unregister(playerCPManager);
        NeoForge.EVENT_BUS.unregister(this);
        activeContexts.values().forEach(set ->
                set.forEach(NeoForge.EVENT_BUS::unregister));
        activeContexts.clear();
    }

    public void addTask(Runnable runnable) {
        syncManager.addTask(runnable);
    }

    public void halt() {
        syncManager.halt();
    }

    public AbilityCategory getPlayerAbilityCategory(UUID uuid) {
        return playerDataManager.getPlayerAbilityCategory(uuid);
    }

    public void setPlayerAbilityCategory(UUID uuid, AbilityCategory abilityCategory) {
        playerDataManager.setPlayerAbilityCategory(uuid, abilityCategory);
    }


    /**
     * 技能数据相关方法
     */
    public float getPlayerSkillExp(UUID uuid, String skillKey) {
        return skillDataManager.getSkillExp(uuid, skillKey);
    }

    public void addPlayerSkillExp(UUID uuid, Skill skill, SkillDataManager.ExpEvent expEvent) {
        skillDataManager.addSkillExp(uuid, skill, expEvent);
        playerCPManager.addLevelProgress(uuid, expEvent.getIncrement());
    }

    public void addPlayerSkill(ServerPlayer serverPlayer, String skillKey) {
        skillDataManager.addSkill(serverPlayer, skillKey);
    }

    public void removePlayerSkill(UUID uuid, String skillKey) {
        skillDataManager.removeSkill(uuid, skillKey);
    }

    public void toggleSkill(UUID uuid, String skillId) {
        skillDataManager.toggleSkill(uuid, skillId);
    }

    public void releaseMaintenanceOccupation(UUID uuid, String skillId) {
        playerCPManager.releaseMaintenanceOccupation(uuid, skillId);
    }

    public int getPlayerSkillLevel(UUID uuid, String skillKey) {
        return skillDataManager.getSkillLevel(uuid, skillKey);
    }


    /**
     * CP相关方法
     */
    public float getPlayerOccupiedCP(UUID uuid) {
        return playerCPManager.getOccupiedCP(uuid);
    }

    public int getPlayerLevel(UUID uuid) {
        return playerCPManager.getLevel(uuid);
    }

    /**
     * 请求CP占用，cost为动态计算
     */
    public boolean castCpIfPossible(ServerPlayer player, Skill skill,
                                    Skill.CostCalculator calculator,
                                    Skill.SkillAction action) {
        var uuid = player.getUUID();
        var level = getPlayerSkillLevel(uuid, skill.getKeyString());
        var ctx = new Skill.SkillContext(level, playerCPManager.getAvailableCP(uuid), this);

        if (DEV_MODE) {
            action.execute(ctx, 0f);
            return true;
        }

        var actualCost = calculator.calculate(ctx);
        if (playerCPManager.tryOccupation(uuid, actualCost, skill, skill.getIterationTicks(level), false)) {
            action.execute(ctx, actualCost);
            addPlayerSkillExp(uuid, skill, SkillDataManager.ExpEvent.ACT_EFFECTIVE);
            return true;
        }
        return false;
    }

    /**
     * 请求CP占用，cost为静态值
     */
    public boolean castCpIfPossible(ServerPlayer player, Skill skill,
                                    float cost,
                                    Skill.SkillAction action) {
        var uuid = player.getUUID();
        var level = getPlayerSkillLevel(uuid, skill.getKeyString());
        var ctx = new Skill.SkillContext(level, playerCPManager.getAvailableCP(uuid), this);

        if (DEV_MODE) {
            action.execute(ctx, 0f);
            return true;
        }

        if (playerCPManager.tryOccupation(uuid, cost, skill, skill.getIterationTicks(level), false)) {
            action.execute(ctx, cost);
            addPlayerSkillExp(uuid, skill, SkillDataManager.ExpEvent.ACT_EFFECTIVE);
            return true;
        }
        return false;
    }

    public boolean tryPermanentOccupation(UUID uuid, float amount, Skill skill) {
        return playerCPManager.tryOccupation(uuid, amount, skill, 0, true);
    }

    public void setPlayerLevel(UUID uuid, int level) {
        playerCPManager.setLevel(uuid, level);
    }

    public float getPlayerAbilityExp(UUID uuid) {
        return playerCPManager.getAbilityExp(uuid);
    }

    public void setPlayerAbilityExp(UUID uuid, float amount) {
        playerCPManager.setAbilityExp(uuid, amount);
    }

    public void addPlayerAbilityExp(UUID uuid, float amount) {
        playerCPManager.addLevelProgress(uuid, amount);
    }

    public boolean canPlayerLevelUp(UUID uuid) {
        return playerCPManager.canLevelUp(uuid);
    }

    public float getPlayerAvailableCP(UUID uuid) {
        return playerCPManager.getAvailableCP(uuid);
    }

    public void setPlayerAvailableCP(UUID uuid, float availableCP) {
        playerCPManager.setAvailableCP(uuid, availableCP);
    }

    public float getPlayerMaxCP(UUID uuid) {
        return playerCPManager.getMaxCP(uuid);
    }

    public void setPlayerMaxCP(UUID uuid, float maxCP) {
        playerCPManager.setMaxCP(uuid, maxCP);
    }

    public AbilityData.Status getPlayerStatus(UUID uuid) {
        return playerCPManager.getStatus(uuid);
    }

    public void setPlayerStatus(UUID uuid, AbilityData.Status status) {
        playerCPManager.setStatus(uuid, status);
    }

    public int getPlayerStateTimer(UUID uuid) {
        return playerCPManager.getStateTimer(uuid);
    }

    public void setPlayerStateTimer(UUID uuid, int stateTimer) {
        playerCPManager.setStateTimer(uuid, stateTimer);
    }

    public int getPlayerCurrSP(UUID uuid) {
        return playerCPManager.getCurrSP(uuid);
    }

    public void setPlayerCurrSP(UUID uuid, int currSP) {
        playerCPManager.setCurrSP(uuid, currSP);
    }

    public void addPlayerCurrSP(UUID uuid, int currSP) {
        playerCPManager.addCurrSP(uuid, currSP);
    }

    public int getPlayerMaxSP(UUID uuid) {
        return playerCPManager.getMaxSP(uuid);
    }

    public void setPlayerMaxSP(UUID uuid, int maxSP) {
        playerCPManager.setMaxSP(uuid, maxSP);
    }

    public float getPlayerFreeCPRatio(UUID uuid) {
        return playerCPManager.getFreeCPRatio(uuid);
    }

    public float getPlayerDamageMultiplier(UUID uuid) {
        return playerCPManager.getDamageMultiplier(uuid);
    }

    public float getPlayerRangeMultiplier(UUID uuid) {
        return playerCPManager.getRangeMultiplier(uuid);
    }

    public float getPlayerEffectiveDistanceMultiplier(UUID uuid) {
        return playerCPManager.getEffectiveDistanceMultiplier(uuid);
    }

    public float getPlayerCurrMP(UUID uuid) {
        return playerCPManager.getCurrMP(uuid);
    }

    public void setPlayerCurrMP(UUID uuid, float currMP) {
        playerCPManager.setCurrMP(uuid, currMP);
    }

    public void addPlayerCurrMP(UUID uuid, float addMP) {
        playerCPManager.addCurrMP(uuid, addMP);
    }

    public boolean tryConsumePlayerMP(UUID uuid, float amount) {
        return playerCPManager.tryConsumeMP(uuid, amount);
    }

    public float getPlayerMaxMP(UUID uuid) {
        return playerCPManager.getMaxMP(uuid);
    }

    public void setPlayerMaxMP(UUID uuid, float maxMP) {
        playerCPManager.setMaxMP(uuid, maxMP);
    }

    public static final class SubsystemRegistry {
        private static final Map<Identifier, AbilitySubsystem> SYNC_ROUTERS = new ConcurrentHashMap<>();

        public static void registerSubsystem(@NotNull AbilitySubsystem subsystem, Identifier syncType) {
            SYNC_ROUTERS.put(syncType, subsystem);
        }

        public static Optional<AbilitySubsystem> getHandler(Identifier type) {
            return Optional.ofNullable(SYNC_ROUTERS.get(type));
        }

        public static List<AbilitySubsystem> getSubsystems() {
            return List.copyOf(SYNC_ROUTERS.values());
        }
    }

    @EventBusSubscriber
    public static final class ServerLifecycleHooks {
        @SubscribeEvent
        public static void tickMinecraftServerThread(ServerTickEvent.Pre event) {
            var server = event.getServer();
            var instance = server.getAcademyCraftServer().getAbilitySystemServer();

            var syncManager = instance.getSyncManager();
            syncManager.processPendingTasks();

            tickDevelopments(server);

            var playerList = server.getPlayerList().getPlayers();
            playerList.forEach(serverPlayer -> {
                SubsystemRegistry.getSubsystems().forEach(abilitySubsystem -> abilitySubsystem.tick(serverPlayer));
                instance.getSyncManager().tick(serverPlayer);
            });

            // Send DevSyncPacket for all developing/completed players
            var devMap = DEVELOP_DATA_MAP;
            var keys = List.copyOf(devMap.keySet());
            for (var uuid : keys) {
                var data = devMap.get(uuid);
                if (data == null || data.getState() == DevState.IDLE) continue;
                var player = server.getPlayerList().getPlayer(uuid);
                if (player == null) continue;
                String message = data.getState() == DevState.DEVELOPING
                        ? "Developing... " + (int) (data.getProgress() * 100) + "%"
                        : data.getState() == DevState.DONE ? "Success!" : "Failed";
                MisakaNetworkServer.send(player, new DevSyncPacket(data.getState(), data.getProgress(), message));
                if (data.getState() != DevState.DEVELOPING) {
                    devMap.remove(uuid);
                }
            }
        }
    }
}
