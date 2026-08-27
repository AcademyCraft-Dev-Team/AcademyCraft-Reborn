package org.academy.api.server.ability;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
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
import org.academy.api.common.ability.darkmatter.DarkmatterResourceService;
import org.academy.api.common.ability.event.AbilityOverloadEvent;
import org.academy.api.common.ability.event.AbilityRecoveryEvent;
import org.academy.api.common.data.AbilityData;
import org.academy.api.common.profiler.AcademyProfiler;
import org.academy.api.common.registries.Registries;
import org.academy.api.common.util.MathUtil;
import org.academy.api.common.wireless.WirelessUser;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.AbilityDevelopmentAccess;
import org.academy.internal.common.ability.level0.skills.OutputControl;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;
import org.academy.internal.common.skilldata.SkillData;
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
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class AbilitySystemServer {
    private static final Logger LOGGER = AcademyCraft.getLogger();
    private static final ConcurrentHashMap<UUID, DevelopData> DEVELOP_DATA_MAP = new ConcurrentHashMap<>();
    private static volatile boolean DEV_MODE = false;
    private final Map<UUID, Set<ServerContext>> activeContexts;
    private final SkillDataManager skillDataManager;
    private final PlayerDataManager playerDataManager;
    private final PlayerCPManager playerCPManager;
    private final DarkmatterResourceManager darkmatterResourceManager;
    private final AeromanipResourceManager aeromanipResourceManager;
    private final PropsManager propsManager;
    private final SyncManager syncManager;
    private final InitialAbilityRecommendationCache initialAbilityRecommendations =
            new InitialAbilityRecommendationCache();

    public AbilitySystemServer(MinecraftServerContext context, WorldData worldData, AbilityConfig abilityConfig) {
        syncManager = new SyncManager(context);

        playerDataManager = new PlayerDataManager(worldData, syncManager);
        SubsystemRegistry.registerSubsystem(playerDataManager, SyncTypes.ABILITY_CATEGORY);

        playerCPManager = new PlayerCPManager(playerDataManager, abilityConfig, syncManager);
        NeoForge.EVENT_BUS.register(playerCPManager);
        SubsystemRegistry.registerSubsystem(playerCPManager, SyncTypes.CP_DATA);

        darkmatterResourceManager = new DarkmatterResourceManager(
                playerDataManager, playerCPManager, syncManager);
        SubsystemRegistry.registerSubsystem(darkmatterResourceManager, SyncTypes.DARKMATTER_STATE);

        aeromanipResourceManager = new AeromanipResourceManager(
                playerDataManager, playerCPManager, syncManager);
        SubsystemRegistry.registerSubsystem(aeromanipResourceManager, SyncTypes.AEROMANIP_RESOURCE);

        propsManager = new PropsManager(playerDataManager, syncManager);
        NeoForge.EVENT_BUS.register(propsManager);
        SubsystemRegistry.registerSubsystem(propsManager, SyncTypes.PROPS_DATA);

        skillDataManager = new SkillDataManager(playerDataManager, syncManager);
        SubsystemRegistry.registerSubsystem(skillDataManager, SyncTypes.SKILL_DATA);
        skillDataManager.setOnSkillLevelUp((uuid, levelsGained) ->
                playerCPManager.refreshCommonSkillBonuses(uuid));
        skillDataManager.setOnSkillSetChanged(playerCPManager::refreshCommonSkillBonuses);
        skillDataManager.setOnProficiencyGain((uuid, amount) -> {
            playerCPManager.addLevelProgress(uuid, amount);
            playerCPManager.refreshCommonSkillBonuses(uuid);
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
        var source = packet.getSource();
        if (!source.portable()
                && player.position().distanceToSqr(Vec3.atCenterOf(source.blockPos())) > 64.0) {
            return;
        }

        var data = DEVELOP_DATA_MAP.get(player.getUUID());
        if (data != null && source.equals(data.getDeveloperSource())) {
            data.abort();
        }
    }

    @HandleFuture
    public static LearnSkillPacket.Response handleLearnSkill(LearnSkillPacket payload) {
        // Legacy instant-learning requests are deliberately rejected. Learning must use StartSkillDevPacket.
        return new LearnSkillPacket.Response(false);
    }

    @HandleFuture
    public static StartSkillDevPacket.Response handleStartSkillDev(StartSkillDevPacket payload) {
        var player = payload.getPacketListener().getPlayer();
        var source = payload.getSource();
        var developer = AbilityDeveloperSources.resolve(player, source);
        if (developer == null) {
            return new StartSkillDevPacket.Response(false, "Invalid developer");
        }
        var level = player.level();

        var skillKey = payload.getSkillName();
        var skillId = Identifier.tryParse(skillKey);
        if (skillId == null) {
            return new StartSkillDevPacket.Response(false, "Invalid skill id");
        }
        var skillRef = Registries.SKILLS.get(skillId);
        if (skillRef.isEmpty()) {
            return new StartSkillDevPacket.Response(false, "Unknown skill");
        }
        var skill = skillRef.get().value();
        var recommendedLevel = skill.getRecommendedLevel().getLevelCode();
        if (!AbilityDevelopmentAccess.canLearnSkill(source, recommendedLevel)) {
            return new StartSkillDevPacket.Response(
                    false,
                    "Portable developer only supports level 1-3 skills"
            );
        }
        var server = level.getServer();
        var instance = server.getAcademyCraftServer().getAbilitySystemServer();
        var playerData = instance.getPlayerData(player.getUUID());
        var developData = getDevelopData(player.getUUID());
        if (developData.isDeveloping()) {
            return new StartSkillDevPacket.Response(false, "Already developing");
        }
        if (!isPlayerReadyForDevelopment(player)) {
            return new StartSkillDevPacket.Response(false, "Player cannot develop abilities now");
        }

        if (!LearningHelper.isSkillAvailableForCategory(
                instance.getPlayerAbilityCategory(player.getUUID()), skill
        )) {
            return new StartSkillDevPacket.Response(false, "Skill is not available for the current category");
        }

        for (var dep : skill.getDependencies()) {
            if (!playerData.isSkillLearned(dep.getKeyString())) {
                return new StartSkillDevPacket.Response(false, "Dependencies not met");
            }
        }

        if (playerData.isSkillLearned(skillKey)) {
            return new StartSkillDevPacket.Response(false, "Already learned");
        }

        var playerLevel = instance.getPlayerLevel(player.getUUID());
        if (playerLevel < recommendedLevel) {
            return new StartSkillDevPacket.Response(false, "Level too low");
        }

        if (developer.extractEnergy(skill.getEnergyCostToLearn(), true) < skill.getEnergyCostToLearn()) {
            return new StartSkillDevPacket.Response(false, "Insufficient energy");
        }

        for (var cond : skill.getDevConditions()) {
            if (!cond.accepts(player, developer)) {
                return new StartSkillDevPacket.Response(false, cond.getHintText());
            }
        }

        var started = developData.start(new DevelopAction() {
            @Override
            public int getTotalTicks() {
                return 200; // 10 seconds at 20 ticks/sec
            }

            @Override
            public int getEnergyCost() {
                return skill.getEnergyCostToLearn();
            }

            @Override
            public String getTargetId() {
                return skillKey;
            }

            @Override
            public boolean validate(ServerPlayer sp, WirelessUser dev) {
                return canDevelopSkill(sp, dev, skill, source);
            }

            @Override
            public void onComplete(ServerPlayer sp, WirelessUser dev) {
                instance.addPlayerSkill(sp, skillKey);
            }
        }, source, player.level().dimension());

        return new StartSkillDevPacket.Response(started, started ? "Started" : "Already developing");
    }

    @HandleFuture
    public static StartLevelDevPacket.Response handleStartLevelDev(StartLevelDevPacket payload) {
        var player = payload.getPacketListener().getPlayer();
        var source = payload.getSource();
        var developer = AbilityDeveloperSources.resolve(player, source);
        if (developer == null) {
            return new StartLevelDevPacket.Response(false, "Invalid developer");
        }
        var level = player.level();

        var server = level.getServer();
        var instance = server.getAcademyCraftServer().getAbilitySystemServer();
        var currentCategory = instance.getPlayerAbilityCategory(player.getUUID());
        var initialDevelopment = currentCategory == AbilityCategories.LEVEL0.get();
        var mode = payload.getMode();
        if (!initialDevelopment && mode != StartLevelDevPacket.Mode.DIRECT) {
            return new StartLevelDevPacket.Response(
                    false,
                    "P.R.O.P.S confirmation is only available for initial development"
            );
        }
        var currentLevel = instance.getPlayerLevel(player.getUUID());
        var developData = getDevelopData(player.getUUID());
        if (developData.isDeveloping()) {
            return new StartLevelDevPacket.Response(false, "Already developing");
        }
        if (!initialDevelopment && currentLevel >= 5) {
            return new StartLevelDevPacket.Response(false, "Already max level");
        }

        if (!initialDevelopment && !instance.canPlayerLevelUp(player.getUUID())) {
            return new StartLevelDevPacket.Response(false, "Ability exp not full");
        }

        // A missing category is always the initial Level 0 development, even if a legacy save
        // contains an inconsistent CP level. Completing it repairs the player to category Level 1.
        var plan = LevelDevelopmentPlan.create(initialDevelopment, currentLevel);
        if (!AbilityDevelopmentAccess.canDevelopAbilityLevel(source, plan.targetLevel())) {
            return new StartLevelDevPacket.Response(
                    false,
                    "Ability levels 4-5 require the full-size Ability Developer"
            );
        }
        var cost = plan.energyCost();
        if (!canDevelopLevel(
                player, developer, source, currentCategory, currentLevel,
                plan.targetLevel(), initialDevelopment, cost
        )) {
            return new StartLevelDevPacket.Response(false, "Development conditions not met");
        }
        if (developer.extractEnergy(cost, true) < cost) {
            return new StartLevelDevPacket.Response(false, "Insufficient energy");
        }

        AbilityCategory selectedInitialCategory = null;
        if (initialDevelopment) {
            var propsData = instance.getPlayerData(player.getUUID()).getPropsData();
            if (mode == StartLevelDevPacket.Mode.DIRECT && propsData.isStarted()) {
                mode = StartLevelDevPacket.Mode.PREVIEW;
            }
            if (mode == StartLevelDevPacket.Mode.PREVIEW) {
                if (!propsData.isStarted()) {
                    return new StartLevelDevPacket.Response(false, "P.R.O.P.S is not initialized");
                }
                var recommendation = InitialAbilitySelector.choose(
                        Registries.ABILITY_CATEGORIES,
                        propsData::get
                );
                if (recommendation != null) {
                    instance.initialAbilityRecommendations.put(
                        player.getUUID(),
                        recommendation,
                        player.level().dimension().identifier(),
                        source.cacheKey(),
                        player.level().getGameTime()
                    );
                    return new StartLevelDevPacket.Response(
                            StartLevelDevPacket.Response.Status.CONFIRMATION_REQUIRED,
                            "P.R.O.P.S confirmation required",
                            recommendation.getKey()
                    );
                }
                mode = StartLevelDevPacket.Mode.DIRECT;
            }

            if (mode == StartLevelDevPacket.Mode.ACCEPT_PROPS
                    || mode == StartLevelDevPacket.Mode.RANDOM) {
                var recommendation = instance.initialAbilityRecommendations.consume(
                        player.getUUID(),
                        player.level().dimension().identifier(),
                        source.cacheKey(),
                        player.level().getGameTime()
                );
                if (recommendation == null || !propsData.isStarted()) {
                    return new StartLevelDevPacket.Response(false, "P.R.O.P.S recommendation expired");
                }
                selectedInitialCategory = mode == StartLevelDevPacket.Mode.ACCEPT_PROPS
                        ? recommendation
                        : chooseInitialAbilityCategory();
            } else if (mode == StartLevelDevPacket.Mode.DIRECT) {
                instance.initialAbilityRecommendations.clear(player.getUUID());
                selectedInitialCategory = chooseInitialAbilityCategory();
            } else {
                return new StartLevelDevPacket.Response(false, "Invalid initial development mode");
            }
        }

        var frozenInitialCategory = selectedInitialCategory;

        var started = developData.start(new DevelopAction() {
            @Override
            public int getTotalTicks() {
                return 400; // 20 seconds
            }

            @Override
            public int getEnergyCost() {
                return cost;
            }

            @Override
            public String getTargetId() {
                return DevelopAction.LEVEL_TARGET_ID;
            }

            @Override
            public boolean validate(ServerPlayer sp, WirelessUser dev) {
                return canDevelopLevel(
                        sp, dev, source,
                        currentCategory,
                        currentLevel,
                        plan.targetLevel(),
                        initialDevelopment,
                        cost
                );
            }

            @Override
            public void onComplete(ServerPlayer sp, WirelessUser dev) {
                if (initialDevelopment) {
                    instance.setPlayerAbilityCategory(sp.getUUID(), frozenInitialCategory);
                    instance.setPlayerLevel(sp.getUUID(), plan.targetLevel());
                    instance.setPlayerBaseMaxCP(sp.getUUID(), AbilityLevel.LEVEL1.getBasicCP());
                } else {
                    instance.setPlayerLevel(sp.getUUID(), plan.targetLevel());
                    var levels = AbilityLevel.values();
                    if (plan.targetLevel() < levels.length) {
                        instance.setPlayerBaseMaxCP(sp.getUUID(), levels[plan.targetLevel()].getBasicCP());
                    }
                }
            }
        }, source, player.level().dimension());

        return new StartLevelDevPacket.Response(started, started ? "Started" : "Already developing");
    }

    private static boolean canDevelopSkill(
            ServerPlayer player,
            WirelessUser developer,
            Skill skill,
            DevelopmentSource source
    ) {
        if (!isPlayerReadyForDevelopment(player)) return false;
        if (!AbilityDevelopmentAccess.canLearnSkill(
                source, skill.getRecommendedLevel().getLevelCode())) return false;
        var instance = getSystem(player);
        var playerData = instance.getPlayerData(player.getUUID());
        if (!LearningHelper.isSkillAvailableForCategory(instance.getPlayerAbilityCategory(player.getUUID()), skill)
                || playerData.isSkillLearned(skill.getKeyString())
                || instance.getPlayerLevel(player.getUUID()) < skill.getRecommendedLevel().getLevelCode()) {
            return false;
        }
        for (var dependency : skill.getDependencies()) {
            if (!playerData.isSkillLearned(dependency.getKeyString())) return false;
        }
        for (var condition : skill.getDevConditions()) {
            if (!condition.accepts(player, developer)) return false;
        }
        return true;
    }

    private static boolean canDevelopLevel(
            ServerPlayer player,
            WirelessUser developer,
            DevelopmentSource source,
            AbilityCategory expectedCategory,
            int expectedLevel,
            int expectedTargetLevel,
            boolean initialDevelopment,
            int expectedCost
    ) {
        if (!isPlayerReadyForDevelopment(player)) return false;
        if (!AbilityDevelopmentAccess.canDevelopAbilityLevel(source, expectedTargetLevel)) {
            return false;
        }
        var instance = getSystem(player);
        var currentCategory = instance.getPlayerAbilityCategory(player.getUUID());
        if (initialDevelopment) {
            return currentCategory == AbilityCategories.LEVEL0.get()
                    && LearningHelper.getEstimatedLevelUpConsumption(0) == expectedCost;
        }
        if (currentCategory != expectedCategory
                || instance.getPlayerLevel(player.getUUID()) != expectedLevel
                || expectedLevel < 0
                || expectedLevel >= 5
                || LearningHelper.getEstimatedLevelUpConsumption(expectedLevel) != expectedCost) {
            return false;
        }
        return instance.canPlayerLevelUp(player.getUUID());
    }

    private static boolean isPlayerReadyForDevelopment(ServerPlayer player) {
        return player.isAlive() && !player.hasDisconnected() && !player.isSpectator();
    }

    private static AbilityCategory chooseInitialAbilityCategory() {
        var candidates = new ArrayList<AbilityCategory>();
        var weightedRandom = new MathUtil.WeightedRandom<AbilityCategory>();
        for (var category : Registries.ABILITY_CATEGORIES) {
            if (category == AbilityCategories.LEVEL0.get()) continue;
            candidates.add(category);
            weightedRandom.addItem(category, category.getProbability());
        }
        if (candidates.isEmpty()) {
            throw new IllegalStateException("No ability category is available for initial development");
        }
        var selected = weightedRandom.getRandomItem();
        return selected != null ? selected : candidates.get(MathUtil.RANDOM.nextInt(candidates.size()));
    }

    public static void registerContext(ServerContext serverContext) {
        var player = serverContext.player;

        var instance = getSystem(player);
        instance.activeContexts.computeIfAbsent(player.getUUID(), _ -> ConcurrentHashMap.newKeySet())
                .add(serverContext);

        NeoForge.EVENT_BUS.register(serverContext.eventListener());
        MisakaNetworkServer.NETWORK_MANAGER.register(serverContext);
    }

    public static void unregisterContext(ServerContext serverContext) {
        var player = serverContext.player;

        getSystem(player).removeContext(serverContext);
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

    public static void tickDevelopments(MinecraftServer server) {
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

    private static int resolveIterationPoints(int configuredPoints, float baseCost) {
        if (configuredPoints > 0) return configuredPoints;
        return Math.max(1, Mth.ceil(baseCost * 0.5f));
    }

    private void removeContext(ServerContext serverContext) {
        var player = serverContext.player;
        var contexts = activeContexts.get(player.getUUID());
        if (contexts == null || !contexts.remove(serverContext)) return;

        if (contexts.isEmpty()) activeContexts.remove(player.getUUID(), contexts);

        NeoForge.EVENT_BUS.unregister(serverContext.eventListener());
        MisakaNetworkServer.NETWORK_MANAGER.unregister(serverContext);
        serverContext.onUnregistered();
    }

    public void schedulePlayerSync(UUID uuid, Identifier syncType) {
        syncManager.schedulePlayerSync(uuid, syncType);
    }

    public void onPlayerLogin(ServerPlayer player) {
        syncManager.onPlayerLogin(player);
        playerDataManager.onPlayerLogin(player);
        for (var sub : SubsystemRegistry.getSubsystems()) {
            sub.onPlayerLogin(player);
        }
        normalizePlayerLevelForCategory(
                player.getUUID(),
                playerDataManager.getPlayerAbilityCategory(player.getUUID())
        );
    }

    public void onPlayerLogout(ServerPlayer player) {
        var development = DEVELOP_DATA_MAP.remove(player.getUUID());
        if (development != null) development.abort();
        initialAbilityRecommendations.clear(player.getUUID());
        try {
            for (var sub : SubsystemRegistry.getSubsystems()) {
                sub.onPlayerLogout(player);
            }

            var contexts = activeContexts.get(player.getUUID());
            if (contexts != null) List.copyOf(contexts).forEach(ServerContext::unregister);
        } finally {
            syncManager.onPlayerLogout(player);
        }
    }

    public SyncManager getSyncManager() {
        return syncManager;
    }

    public PropsManager getPropsManager() {
        return propsManager;
    }

    public boolean resetProps(ServerPlayer player) {
        initialAbilityRecommendations.clear(player.getUUID());
        return propsManager.reset(player);
    }

    public DarkmatterResourceManager getDarkmatterResourceManager() {
        return darkmatterResourceManager;
    }

    public DarkmatterResourceService getDarkmatterResourceService() {
        return darkmatterResourceManager;
    }

    public AeromanipResourceManager getAeromanipResourceManager() {
        return aeromanipResourceManager;
    }

    public Player getPlayerData(UUID uuid) {
        return Objects.requireNonNull(playerDataManager.getData(uuid));
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
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var uuid = player.getUUID();
        var playerData = getPlayerData(uuid);

        playerData.getSkillDataMap().forEach((skillId, data) -> {
            if (!data.isEnabled()) return;
            Registries.SKILLS.get(Identifier.parse(skillId)).ifPresent(skillRef -> {
                var skill = skillRef.value();
                var maintenanceCost = skill.getMaintenanceCost(player);
                if (maintenanceCost > 0) {
                    tryPermanentOccupation(uuid, maintenanceCost, skill);
                }
            });
        });
        LOGGER.info("player {} recovered from overload.", player.getName().getString());
    }

    public void onServerStopping() {
        DEVELOP_DATA_MAP.values().forEach(DevelopData::abort);
        DEVELOP_DATA_MAP.clear();
        NeoForge.EVENT_BUS.unregister(playerCPManager);
        NeoForge.EVENT_BUS.unregister(this);
        activeContexts.values().stream()
                .flatMap(Set::stream)
                .toList()
                .forEach(ServerContext::unregister);
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
        changePlayerAbilityCategory(uuid, abilityCategory, false);
    }

    public void replacePlayerAbilityCategory(UUID uuid, AbilityCategory abilityCategory) {
        changePlayerAbilityCategory(uuid, abilityCategory, true);
    }

    private void changePlayerAbilityCategory(
            UUID uuid,
            AbilityCategory abilityCategory,
            boolean clearCategorySkills
    ) {
        initialAbilityRecommendations.clear(uuid);
        var previousCategory = playerDataManager.getPlayerAbilityCategory(uuid);
        var categoryChanged = previousCategory != abilityCategory;
        if (!categoryChanged && !clearCategorySkills) return;

        var playerData = getPlayerData(uuid);
        if (playerData != null && !clearCategorySkills) {
            playerData.getSkillDataMap().forEach((skillId, data) -> {
                if (!data.isEnabled()) return;
                var id = Identifier.tryParse(skillId);
                if (id == null) return;
                Registries.SKILLS.get(id).ifPresent(skillReference -> {
                    var skill = skillReference.value();
                    if (LearningHelper.isSkillAvailableForCategory(abilityCategory, skill)) return;
                    skillDataManager.toggleSkill(uuid, skillId);
                    playerCPManager.releaseMaintenanceOccupation(uuid, skillId);
                });
            });
        }

        var contexts = activeContexts.get(uuid);
        if (contexts != null) {
            List.copyOf(contexts).forEach(ServerContext::unregister);
        }
        if (categoryChanged) {
            playerDataManager.setPlayerAbilityCategory(uuid, abilityCategory);
            // Resource managers reinitialize the pool for the new category on the next tick.
            playerCPManager.setCurrMP(uuid, 0.0f);
            playerCPManager.setMaxMP(uuid, 0.0f);
        }
        if (clearCategorySkills) {
            skillDataManager.clearCategorySkills(uuid);
        }
        normalizePlayerLevelForCategory(uuid, abilityCategory);
        playerCPManager.refreshCommonSkillBonuses(uuid);
        playerCPManager.releaseAllOccupations(uuid);
    }

    private void normalizePlayerLevelForCategory(UUID uuid, AbilityCategory category) {
        var currentLevel = playerCPManager.getLevel(uuid);
        var normalizedLevel = LevelDevelopmentPlan.normalizeLevelForCategory(
                category == AbilityCategories.LEVEL0.get(),
                currentLevel
        );
        if (normalizedLevel == currentLevel) return;

        playerCPManager.setLevel(uuid, normalizedLevel);
        playerCPManager.setBaseMaxCP(
                uuid,
                AbilityLevel.fromLevelCode(normalizedLevel).getBasicCP()
        );
    }

    public float getPlayerSkillExp(UUID uuid, String skillKey) {
        return getPlayerSkillProficiency(uuid, skillKey);
    }

    /**
     * @deprecated Use {@link #addPlayerSkillProficiency(UUID, Skill, ProficiencyEvent)}.
     */
    @Deprecated
    public void addPlayerSkillExp(UUID uuid, Skill skill, SkillDataManager.ExpEvent expEvent) {
        skillDataManager.addSkillExp(uuid, skill, expEvent);
    }

    public float getPlayerSkillProficiency(UUID uuid, String skillKey) {
        return skillDataManager.getSkillProficiency(uuid, skillKey);
    }

    public boolean addPlayerSkillProficiency(UUID uuid, Skill skill, ProficiencyEvent event) {
        return skillDataManager.addSkillProficiency(uuid, skill, event);
    }

    public boolean addPlayerSkillProficiency(UUID uuid, Skill skill, float amount) {
        return skillDataManager.addSkillProficiency(uuid, skill, amount);
    }

    public boolean setPlayerSkillProficiency(UUID uuid, Skill skill, float proficiency) {
        return skillDataManager.setSkillProficiency(uuid, skill, proficiency);
    }

    public void reportSkillActivity(UUID uuid, Skill skill, SkillActivity activity) {
        skillDataManager.reportSkillActivity(uuid, skill, activity);
    }

    public <T extends SkillData> boolean updatePlayerSkillData(
            UUID uuid,
            Skill skill,
            Class<T> type,
            Consumer<T> action
    ) {
        return skillDataManager.mutate(uuid, skill.getKeyString(), type, action);
    }

    public void addPlayerSkill(ServerPlayer serverPlayer, String skillKey) {
        skillDataManager.addSkill(serverPlayer, skillKey);
    }

    public void removePlayerSkill(UUID uuid, String skillKey) {
        skillDataManager.removeSkill(uuid, skillKey);
    }

    public void toggleSkill(UUID uuid, String skillId) {
        skillDataManager.toggleSkill(uuid, skillId);
        var playerData = getPlayerData(uuid);
        var skillData = playerData == null ? null : playerData.getSkillDataMap().get(skillId);
        if (skillData == null || !skillData.isEnabled()) {
            playerCPManager.releaseMaintenanceOccupation(uuid, skillId);
        }
    }

    public void releaseMaintenanceOccupation(UUID uuid, String skillId) {
        playerCPManager.releaseMaintenanceOccupation(uuid, skillId);
    }

    public int getPlayerSkillLevel(UUID uuid, String skillKey) {
        return skillDataManager.getSkillLevel(uuid, skillKey);
    }

    public float getPlayerOccupiedCP(UUID uuid) {
        return playerCPManager.getOccupiedCP(uuid);
    }

    public boolean isPlayerSkillDebugMode(UUID uuid) {
        return playerCPManager.isSkillDebugMode(uuid);
    }

    public boolean togglePlayerSkillDebugMode(UUID uuid) {
        return playerCPManager.toggleSkillDebugMode(uuid);
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
        return castCpIfPossible(player, skill, calculator, action, true, true, null, Skill.NO_STACK_LIMIT);
    }

    public boolean castCpIfPossible(ServerPlayer player, Skill skill,
                                    Skill.CostCalculator calculator,
                                    Skill.SkillAction action,
                                    String stackGroup,
                                    int stackLimit) {
        return castCpIfPossible(player, skill, calculator, action, true, true, stackGroup, stackLimit);
    }

    public boolean castContinuousCpIfPossible(ServerPlayer player, Skill skill,
                                              Skill.CostCalculator calculator,
                                              Skill.SkillAction action,
                                              boolean effective) {
        return castCpIfPossible(player, skill, calculator, action, false, effective, null, Skill.NO_STACK_LIMIT);
    }

    public boolean castCpAndMpIfPossible(
            ServerPlayer player,
            Skill skill,
            Skill.CostCalculator cpCalculator,
            Skill.CostCalculator mpCalculator,
            Skill.SkillAction action
    ) {
        return castCpAndMpIfPossible(
                player, skill, cpCalculator, mpCalculator, action,
                true, true, null, Skill.NO_STACK_LIMIT);
    }

    public boolean castCpAndMpIfPossible(
            ServerPlayer player,
            Skill skill,
            Skill.CostCalculator cpCalculator,
            Skill.CostCalculator mpCalculator,
            Skill.SkillAction action,
            String stackGroup,
            int stackLimit
    ) {
        return castCpAndMpIfPossible(
                player, skill, cpCalculator, mpCalculator, action,
                true, true, stackGroup, stackLimit);
    }

    public boolean castContinuousCpAndMpIfPossible(
            ServerPlayer player,
            Skill skill,
            Skill.CostCalculator cpCalculator,
            Skill.CostCalculator mpCalculator,
            Skill.SkillAction action,
            boolean effective
    ) {
        return castCpAndMpIfPossible(
                player, skill, cpCalculator, mpCalculator, action,
                false, effective, null, Skill.NO_STACK_LIMIT);
    }

    private boolean castCpIfPossible(ServerPlayer player, Skill skill,
                                     Skill.CostCalculator calculator,
                                     Skill.SkillAction action,
                                     boolean discreteTrigger,
                                     boolean effective,
                                     String stackGroup,
                                     int stackLimit) {
        var uuid = player.getUUID();
        var level = getPlayerSkillLevel(uuid, skill.getKeyString());
        var proficiency = skill.getProficiency(player);
        var milestone = skill.getEffectiveProficiencyMilestone(player);
        var ctx = new Skill.SkillContext(
                level,
                proficiency,
                milestone,
                playerCPManager.getAvailableCP(uuid),
                this
        );

        var baseCost = calculator.calculate(ctx);
        if (!Float.isFinite(baseCost) || baseCost < 0) return false;
        var actualCost = Math.max(0, OutputControl.adjustCpCost(this, uuid, skill, baseCost)
                * playerCPManager.getCalculationIntensity(uuid));
        var iterationPoints = resolveIterationPoints(skill.getIterationTicks(player), baseCost);
        if (playerCPManager.tryOccupation(
                uuid, actualCost, skill, iterationPoints, false, stackGroup, stackLimit
        )) {
            EntityMotionGuard.runWithMotionSource(
                    player,
                    () -> action.execute(ctx, actualCost)
            );
            if (discreteTrigger) addPlayerSkillProficiency(uuid, skill, ProficiencyEvent.TRIGGER);
            else reportSkillActivity(uuid, skill, effective ? SkillActivity.EFFECTIVE : SkillActivity.ACTIVE);
            return true;
        }
        return false;
    }

    private boolean castCpAndMpIfPossible(
            ServerPlayer player,
            Skill skill,
            Skill.CostCalculator cpCalculator,
            Skill.CostCalculator mpCalculator,
            Skill.SkillAction action,
            boolean discreteTrigger,
            boolean effective,
            String stackGroup,
            int stackLimit
    ) {
        if (cpCalculator == null || mpCalculator == null || action == null) return false;
        var uuid = player.getUUID();
        var level = getPlayerSkillLevel(uuid, skill.getKeyString());
        var proficiency = skill.getProficiency(player);
        var milestone = skill.getEffectiveProficiencyMilestone(player);
        var ctx = new Skill.SkillContext(
                level,
                proficiency,
                milestone,
                playerCPManager.getAvailableCP(uuid),
                this
        );

        var baseCpCost = cpCalculator.calculate(ctx);
        var compressedAirCost = mpCalculator.calculate(ctx);
        if (!Float.isFinite(baseCpCost) || baseCpCost < 0.0f
                || !Float.isFinite(compressedAirCost) || compressedAirCost < 0.0f) return false;
        var actualCpCost = Math.max(0.0f,
                OutputControl.adjustCpCost(this, uuid, skill, baseCpCost)
                        * playerCPManager.getCalculationIntensity(uuid));
        var iterationPoints = resolveIterationPoints(skill.getIterationTicks(player), baseCpCost);
        if (!aeromanipResourceManager.tryCast(
                player, skill, actualCpCost, compressedAirCost, iterationPoints,
                stackGroup, stackLimit)) return false;

        EntityMotionGuard.runWithMotionSource(player, () -> action.execute(ctx, actualCpCost));
        if (discreteTrigger) addPlayerSkillProficiency(uuid, skill, ProficiencyEvent.TRIGGER);
        else reportSkillActivity(uuid, skill, effective ? SkillActivity.EFFECTIVE : SkillActivity.ACTIVE);
        return true;
    }

    /**
     * 请求CP占用，cost为静态值
     */
    public boolean castCpIfPossible(ServerPlayer player, Skill skill,
                                    float cost,
                                    Skill.SkillAction action) {
        return castCpIfPossible(player, skill, _ -> cost, action);
    }

    public boolean castCpIfActionSucceeds(
            ServerPlayer player,
            Skill skill,
            float cost,
            BooleanSupplier action
    ) {
        if (action == null || !Float.isFinite(cost) || cost < 0) return false;
        var uuid = player.getUUID();
        var actualCost = Math.max(0, OutputControl.adjustCpCost(this, uuid, skill, cost)
                * playerCPManager.getCalculationIntensity(uuid));
        var level = getPlayerSkillLevel(uuid, skill.getKeyString());
        if (!playerCPManager.tryOccupationIf(
                uuid,
                actualCost,
                skill,
                resolveIterationPoints(skill.getIterationTicks(player), cost),
                () -> Boolean.TRUE.equals(EntityMotionGuard.callWithMotionSource(
                        player,
                        action::getAsBoolean
                ))
        )) {
            return false;
        }
        addPlayerSkillExp(uuid, skill, SkillDataManager.ExpEvent.ACT_EFFECTIVE);
        return true;
    }

    public boolean tryPermanentOccupation(UUID uuid, float amount, Skill skill) {
        if (!Float.isFinite(amount) || amount < 0) return false;
        var actualAmount = Math.max(
                0, amount * playerCPManager.getCalculationIntensity(uuid));
        return playerCPManager.tryOccupation(uuid, actualAmount, skill, 0, true);
    }

    public boolean tryTimedOccupation(UUID uuid, float amount, Skill skill) {
        var level = getPlayerSkillLevel(uuid, skill.getKeyString());
        return tryTimedOccupation(uuid, amount, skill, skill.getIterationTicks(level));
    }

    public boolean tryTimedOccupation(ServerPlayer player, float amount, Skill skill) {
        var adjusted = skill.adjustProficiencyCost(
                player,
                SkillProficiencyProfile.CostKind.DYNAMIC,
                amount
        );
        return tryTimedOccupation(player.getUUID(), adjusted, skill, skill.getIterationTicks(player));
    }

    public boolean tryTimedOccupation(UUID uuid, float amount, Skill skill, int iterationPoints) {
        return tryTimedOccupation(uuid, amount, skill, iterationPoints, false);
    }

    /** Reserves the independently paid attack portion of a maintained damage skill. */
    public boolean tryTimedAttackOccupation(
            UUID uuid,
            float amount,
            Skill skill,
            int iterationPoints
    ) {
        return tryTimedOccupation(uuid, amount, skill, iterationPoints, true);
    }

    private boolean tryTimedOccupation(
            UUID uuid,
            float amount,
            Skill skill,
            int iterationPoints,
            boolean outputAdjustable
    ) {
        if (!Float.isFinite(amount) || amount < 0) return false;
        var adjusted = outputAdjustable
                ? OutputControl.adjustCpCost(this, uuid, skill, amount)
                : amount;
        var actualAmount = Math.max(
                0, adjusted * playerCPManager.getCalculationIntensity(uuid));
        return playerCPManager.tryOccupation(
                uuid,
                actualAmount,
                skill,
                resolveIterationPoints(iterationPoints, amount),
                false
        );
    }

    /** Atomically reserves every timed charge, or reserves none of them. */
    public boolean tryTimedOccupations(
            UUID uuid,
            Skill skill,
            List<TimedOccupationCharge> timedCharges
    ) {
        if (skill == null || timedCharges == null) return false;
        var intensity = playerCPManager.getCalculationIntensity(uuid);
        var actual = new ArrayList<PlayerCPManager.TimedOccupationCharge>(timedCharges.size());
        for (var charge : timedCharges) {
            if (charge == null || !Float.isFinite(charge.amount()) || charge.amount() < 0.0f) {
                return false;
            }
            actual.add(new PlayerCPManager.TimedOccupationCharge(
                    Math.max(0.0f, charge.amount() * intensity),
                    resolveIterationPoints(charge.iterationPoints(), charge.amount())
            ));
        }
        return playerCPManager.tryTimedOccupations(uuid, skill, actual);
    }

    public boolean ensurePermanentOccupation(UUID uuid, float amount, Skill skill) {
        if (!Float.isFinite(amount) || amount < 0) return false;
        var actualAmount = Math.max(
                0, amount * playerCPManager.getCalculationIntensity(uuid));
        return playerCPManager.ensurePermanentOccupation(uuid, actualAmount, skill);
    }

    public boolean replacePermanentOccupation(UUID uuid, float amount, Skill skill) {
        return replacePermanentOccupations(uuid, Map.of(skill, amount));
    }

    public boolean replacePermanentOccupations(UUID uuid, Map<Skill, Float> amounts) {
        if (amounts == null) return false;
        var intensity = playerCPManager.getCalculationIntensity(uuid);
        var actual = new LinkedHashMap<Skill, Float>();
        for (var entry : amounts.entrySet()) {
            var amount = entry.getValue();
            if (entry.getKey() == null || amount == null || !Float.isFinite(amount) || amount < 0) return false;
            actual.put(entry.getKey(), Math.max(0, amount * intensity));
        }
        return playerCPManager.replacePermanentOccupationsAndTryOccupation(
                uuid,
                actual,
                null,
                0,
                0
        );
    }

    public boolean replacePermanentOccupationAndAddTimedOccupations(
            UUID uuid,
            Skill skill,
            float permanentAmount,
            List<TimedOccupationCharge> timedCharges
    ) {
        var actual = prepareMixedOccupationCharges(uuid, skill, permanentAmount, timedCharges);
        return actual != null && playerCPManager.replacePermanentOccupationAndTryTimedOccupations(
                uuid, skill, actual.permanentAmount(), actual.timedCharges());
    }

    public boolean canReplacePermanentOccupationAndAddTimedOccupations(
            UUID uuid,
            Skill skill,
            float permanentAmount,
            List<TimedOccupationCharge> timedCharges
    ) {
        var actual = prepareMixedOccupationCharges(uuid, skill, permanentAmount, timedCharges);
        return actual != null && playerCPManager.canReplacePermanentOccupationAndTryTimedOccupations(
                uuid, skill, actual.permanentAmount(), actual.timedCharges());
    }

    private MixedOccupationCharges prepareMixedOccupationCharges(
            UUID uuid,
            Skill skill,
            float permanentAmount,
            List<TimedOccupationCharge> timedCharges
    ) {
        if (skill == null || timedCharges == null
                || !Float.isFinite(permanentAmount) || permanentAmount < 0.0f) return null;
        var intensity = playerCPManager.getCalculationIntensity(uuid);
        var actualTimed = new ArrayList<PlayerCPManager.TimedOccupationCharge>(timedCharges.size());
        for (var charge : timedCharges) {
            if (charge == null || !Float.isFinite(charge.amount()) || charge.amount() < 0.0f) return null;
            actualTimed.add(new PlayerCPManager.TimedOccupationCharge(
                    Math.max(0.0f, charge.amount() * intensity), charge.iterationPoints()));
        }
        return new MixedOccupationCharges(
                Math.max(0.0f, permanentAmount * intensity), List.copyOf(actualTimed));
    }

    public record TimedOccupationCharge(float amount, int iterationPoints) {
    }

    private record MixedOccupationCharges(
            float permanentAmount,
            List<PlayerCPManager.TimedOccupationCharge> timedCharges
    ) {
    }

    public boolean castWithPermanentOccupations(
            ServerPlayer player,
            Skill castSkill,
            float castCost,
            Map<Skill, Float> permanentAmounts
    ) {
        if (castSkill == null || permanentAmounts == null || !Float.isFinite(castCost) || castCost < 0) {
            return false;
        }
        var uuid = player.getUUID();
        var intensity = playerCPManager.getCalculationIntensity(uuid);
        var actualPermanent = new LinkedHashMap<Skill, Float>();
        for (var entry : permanentAmounts.entrySet()) {
            var amount = entry.getValue();
            if (entry.getKey() == null || amount == null || !Float.isFinite(amount) || amount < 0) return false;
            actualPermanent.put(entry.getKey(), Math.max(0, amount * intensity));
        }
        var actualCast = Math.max(0, OutputControl.adjustCpCost(
                this, uuid, castSkill, castCost) * intensity);
        if (!playerCPManager.replacePermanentOccupationsAndTryOccupation(
                uuid,
                actualPermanent,
                castSkill,
                actualCast,
                resolveIterationPoints(castSkill.getIterationTicks(getPlayerSkillLevel(
                        uuid,
                        castSkill.getKeyString()
                )), castCost)
        )) {
            return false;
        }
        addPlayerSkillExp(uuid, castSkill, SkillDataManager.ExpEvent.ACT_EFFECTIVE);
        return true;
    }

    public boolean canCastWithPermanentOccupations(
            ServerPlayer player,
            Skill castSkill,
            float castCost,
            Map<Skill, Float> permanentAmounts
    ) {
        if (castSkill == null || permanentAmounts == null || !Float.isFinite(castCost) || castCost < 0) {
            return false;
        }
        var uuid = player.getUUID();
        var intensity = playerCPManager.getCalculationIntensity(uuid);
        var actualPermanent = new LinkedHashMap<Skill, Float>();
        for (var entry : permanentAmounts.entrySet()) {
            var amount = entry.getValue();
            if (entry.getKey() == null || amount == null || !Float.isFinite(amount) || amount < 0) return false;
            actualPermanent.put(entry.getKey(), Math.max(0, amount * intensity));
        }
        var actualCast = Math.max(0, OutputControl.adjustCpCost(
                this, uuid, castSkill, castCost) * intensity);
        return playerCPManager.canReplacePermanentOccupationsAndTryOccupation(
                uuid,
                actualPermanent,
                castSkill,
                actualCast,
                resolveIterationPoints(castSkill.getIterationTicks(getPlayerSkillLevel(
                        uuid,
                        castSkill.getKeyString()
                )), castCost)
        );
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

    public void setPlayerBaseMaxCP(UUID uuid, float maxCP) {
        playerCPManager.setBaseMaxCP(uuid, maxCP);
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

    public float getPlayerCalculationEfficiency(UUID uuid) {
        return playerCPManager.getCalculationEfficiency(uuid);
    }

    public float getPlayerCalculationIntensity(UUID uuid) {
        return playerCPManager.getCalculationIntensity(uuid);
    }

    public float getPlayerAbilityPowerMultiplier(UUID uuid) {
        return playerCPManager.getAbilityPowerMultiplier(uuid);
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
            AcademyProfiler.runZone("academy.server.tick", () -> {
                var server = event.getServer();
                var instance = server.getAcademyCraftServer().getAbilitySystemServer();

                AcademyProfiler.runZone("academy.server.sync.pending", () -> {
                    var syncManager = instance.getSyncManager();
                    syncManager.processPendingTasks();
                });

                AcademyProfiler.runZone("academy.server.dev", () -> tickDevelopments(server));

                var playerList = server.getPlayerList().getPlayers();
                AcademyProfiler.runZone("academy.server.players", () -> {
                    playerList.forEach(serverPlayer -> {
                        AcademyProfiler.runZone("academy.server.ability.tick", () ->
                                SubsystemRegistry.getSubsystems().forEach(abilitySubsystem -> abilitySubsystem.tick(serverPlayer)));
                        AcademyProfiler.runZone("academy.server.sync.tick", () ->
                                instance.getSyncManager().tick(serverPlayer));
                    });
                });

                AcademyProfiler.runZone("academy.server.dev.sync", () -> {
                    // Send DevSyncPacket for all developing/completed players
                    var devMap = DEVELOP_DATA_MAP;
                    var keys = List.copyOf(devMap.keySet());
                    for (var uuid : keys) {
                        var data = devMap.get(uuid);
                        if (data == null || data.getState() == DevState.IDLE) continue;
                        var player = server.getPlayerList().getPlayer(uuid);
                        if (player == null) continue;
                        MisakaNetworkServer.send(player, new DevSyncPacket(
                                data.getState(), data.getProgress(), data.getTargetId(), data.getMessage()
                        ));
                        if (data.getState() != DevState.DEVELOPING) {
                            devMap.remove(uuid);
                        }
                    }
                });
            });
        }
    }
}
