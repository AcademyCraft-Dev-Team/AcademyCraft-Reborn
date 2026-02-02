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
import org.academy.api.common.data.CPData;
import org.academy.api.common.registries.Registries;
import org.academy.api.common.util.MathUtil;
import org.academy.api.common.wireless.WirelessUser;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.world.level.block.entity.AbilityDeveloperBlockEntity;
import org.academy.internal.server.ability.*;
import org.academy.internal.server.config.AbilityConfig;
import org.academy.internal.server.world.level.storage.Player;
import org.academy.internal.server.world.level.storage.WorldData;
import org.jetbrains.annotations.NotNull;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.future.annotation.HandleFuture;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class AbilitySystemServer {
    private static final Logger LOGGER = AcademyCraft.getLogger();
    private final Map<UUID, Set<ServerContext>> activeContexts;

    private final SkillDataManager skillDataManager;
    private final PlayerDataManager playerDataManager;
    private final PlayerCPManager playerCPManager;
    private final SyncManager syncManager;

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

        MisakaNetworkServer.FUTURE_MANAGER.registerFutureHandler(AbilitySystemServer.class);
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

                var serverContext = (MinecraftServerContext) level.getServer();
                var instance = serverContext.getAcademyCraftServer().getAbilitySystemServer();

                var abilityCategory = weightedRandom.getRandomItem();
                if (abilityCategory != null) {
                    instance.setPlayerAbilityCategory(player.getUUID(), abilityCategory);
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
                var serverContext = (MinecraftServerContext) level.getServer();
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
            MisakaNetworkServer.NETWORK_MANAGER.unregisterPacketListener(ctx);
        });
    }

    @EventBusSubscriber
    public static final class ServerLifecycleHooks {
        @SubscribeEvent
        public static void tickMinecraftServerThread(ServerTickEvent.Pre event) {
            var server = event.getServer();
            var context = (MinecraftServerContext) server;
            var instance = context.getAcademyCraftServer().getAbilitySystemServer();

            var syncManager = instance.getSyncManager();
            syncManager.processPendingTasks();

            var playerList = server.getPlayerList().getPlayers();
            playerList.forEach(serverPlayer -> {
                SubsystemRegistry.getSubsystems().forEach(abilitySubsystem -> abilitySubsystem.tick(serverPlayer));
                instance.getSyncManager().tick(serverPlayer);
            });
        }
    }

    public SyncManager getSyncManager() {
        return syncManager;
    }

    public Player getPlayerData(UUID uuid) {
        return playerDataManager.getData(uuid);
    }

    public static void registerContext(ServerContext serverContext) {
        var player = serverContext.player;
        if (player == null) return;

        var instance = getSystem(player);
        instance.activeContexts.computeIfAbsent(player.getUUID(), k -> ConcurrentHashMap.newKeySet())
                .add(serverContext);

        NeoForge.EVENT_BUS.register(serverContext);
        MisakaNetworkServer.NETWORK_MANAGER.registerPacketListener(serverContext);
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
        MisakaNetworkServer.NETWORK_MANAGER.unregisterPacketListener(serverContext);
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

    public static AbilitySystemServer getSystem(Entity entity) {
        if (entity.level() instanceof ServerLevel serverLevel) {
            return ((MinecraftServerContext) serverLevel.getServer())
                    .getAcademyCraftServer()
                    .getAbilitySystemServer();
        }
        throw new IllegalStateException("Entity is not in a ServerLevel");
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
        var actualCost = calculator.calculate(ctx);

        if (playerCPManager.tryOccupation(uuid, actualCost, skill, skill.getIterationTicks(level), false)) {
            action.execute(ctx, actualCost);
            addPlayerSkillExp(uuid, skill, SkillDataManager.ExpEvent.ACT_EFFECTIVE);
            return true;
        }
        return false;
    }

    /**
     * 请求CP占用cost为静态值
     */
    public boolean castCpIfPossible(ServerPlayer player, Skill skill,
                                    float cost,
                                    Skill.SkillAction action) {
        var uuid = player.getUUID();
        var level = getPlayerSkillLevel(uuid, skill.getKeyString());
        var ctx = new Skill.SkillContext(level, playerCPManager.getAvailableCP(uuid), this);
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

    public CPData.Status getPlayerStatus(UUID uuid) {
        return playerCPManager.getStatus(uuid);
    }

    public void setPlayerStatus(UUID uuid, CPData.Status status) {
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

    public static float getSPReductionRate(LivingEntity entity) {
        return entity.getData(AttachmentTypes.SP_REDUCTION_RATE);
    }

    public static void setSPReductionRate(LivingEntity entity, float rate) {
        var clamped = Mth.clamp(rate, 0.0f, 1.0f);
        if (Float.compare(entity.getData(AttachmentTypes.SP_REDUCTION_RATE), clamped) != 0) {
            entity.setData(AttachmentTypes.SP_REDUCTION_RATE, clamped);
        }
    }
}