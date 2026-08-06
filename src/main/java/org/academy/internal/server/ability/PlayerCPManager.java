package org.academy.internal.server.ability;

import net.minecraft.advancements.AdvancementType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.ability.SyncTypes;
import org.academy.api.common.ability.event.AbilityOverloadEvent;
import org.academy.api.common.ability.event.AbilityRecoveryEvent;
import org.academy.api.common.ability.pakcet.SyncAbilityDataPacket;
import org.academy.api.common.data.AbilityData;
import org.academy.api.common.registries.Registries;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.server.config.AbilityConfig;
import org.academy.internal.common.attribute.PlayerAttributeRuntime;
import org.misaka.MisakaNetworkServer;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.BooleanSupplier;

public class PlayerCPManager implements AbilitySubsystem {
    private static final Logger LOGGER = AcademyCraft.getLogger();
    private static final AbilityLevel[] CACHED_LEVELS = AbilityLevel.values();
    private static final int PERSONAL_REALITY_OVERLOAD_TICKS = 100;
    private static final int MAX_OVERLOAD_TICKS = 1200;
    private static final int MIN_OVERLOAD_TICKS = 200;
    private static final float TICKS_PER_ITERATION_POINT = 20.0f;

    private final PlayerDataManager playerDataManager;
    private final SyncManager syncManager;
    private final AbilityConfig.BrainDevelopmentSettings brainDevelopmentSettings;
    private final Set<UUID> skillDebugPlayers = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, Float> cpIterationProgress = new ConcurrentHashMap<>();

    private final float CP_RATING_OFFSET;
    private final float DAMAGE_MULTIPLIER;

    public PlayerCPManager(PlayerDataManager manager, AbilityConfig config, SyncManager syncManager) {
        playerDataManager = manager;
        this.syncManager = syncManager;

        CP_RATING_OFFSET = config.cpRatingOffset;
        DAMAGE_MULTIPLIER = config.damageMultiplier;
        brainDevelopmentSettings = config.brainDevelopment != null
                ? config.brainDevelopment
                : new AbilityConfig.BrainDevelopmentSettings();
    }

    @Override
    public void onPlayerLogin(ServerPlayer player) {
        if (isAutomaticSkillDebugPlayer(player.getScoreboardName())) {
            skillDebugPlayers.add(player.getUUID());
        }
        refreshCommonSkillBonuses(player.getUUID());
        syncManager.schedulePlayerSync(player.getUUID(), SyncTypes.CP_DATA);
    }

    @Override
    public void onPlayerLogout(ServerPlayer player) {
        skillDebugPlayers.remove(player.getUUID());
        cpIterationProgress.remove(player.getUUID());
    }

    @Override
    public void tick(ServerPlayer player) {
        var playerData = playerDataManager.getData(player.getUUID());
        if (playerData == null) return;

        var cpData = playerData.getCpData();
        var occupations = playerData.getCpOccupations();

        var dirty = false;

        dirty |= switch (cpData.getStatus()) {
            case NORMAL -> tickNormal(cpData);
            case PERSONAL_REALITY_OVERLOAD -> tickWarning(cpData, player);
            case OVERLOAD -> tickOverload(cpData, occupations, player);
        };

        if (cpData.getStatus() != AbilityData.Status.OVERLOAD) {
            dirty |= processOccupations(player, cpData, occupations);
        }

        dirty |= tickSpRegen(player, cpData);

        if (dirty || cpData.isDirty()) {
            playerData.markDirty();
            syncManager.schedulePlayerSync(player.getUUID(), SyncTypes.CP_DATA);
        }
    }

    @Override
    public void processSync(ServerPlayer serverPlayer) {
        var player = playerDataManager.getData(serverPlayer.getUUID());
        if (player == null) return;
        var cpData = player.getCpData();
        MisakaNetworkServer.send(serverPlayer, new SyncAbilityDataPacket(
                cpData.copyWithMaxCP(getMaxCP(serverPlayer.getUUID()))
        ));
        cpData.clearDirty();
    }

    private boolean tickNormal(AbilityData cpData) {
        if (cpData.getAvailableCP() < 0) {
            cpData.setStatus(AbilityData.Status.PERSONAL_REALITY_OVERLOAD);
            cpData.setStateTimer(PERSONAL_REALITY_OVERLOAD_TICKS);
            return true;
        }
        return false;
    }

    private boolean tickWarning(AbilityData cpData, ServerPlayer player) {
        cpData.tickStateTimer();
        // 个人现实超负荷状态下，CP恢复到0时，切换到正常状态
        if (cpData.getAvailableCP() >= 0) {
            cpData.setStatus(AbilityData.Status.NORMAL);
            cpData.setStateTimer(0);
            return true;
        }

        // 个人现实超负荷状态下，定时器结束时，切换到过载状态
        if (cpData.getStateTimer() > 0) return false;
        var maxCP = getMaxCP(player.getUUID());
        if (maxCP <= 0) return false;
        var overflow = -cpData.getAvailableCP();
        var durationSeconds = overflow / (maxCP / 30.0f);
        var finalDurationTicks = Mth.clamp((int) (durationSeconds * 20), MIN_OVERLOAD_TICKS, MAX_OVERLOAD_TICKS);

        NeoForge.EVENT_BUS.post(new AbilityOverloadEvent(player));
        cpData.setStatus(AbilityData.Status.OVERLOAD);
        cpData.setStateTimer(finalDurationTicks);
        return true;
    }

    private boolean tickOverload(AbilityData cpData, List<AbilityData.CpOccupationData> occupations, ServerPlayer player) {
        cpData.tickStateTimer();
        if (cpData.getStateTimer() <= 0) {
            cpData.setStatus(AbilityData.Status.NORMAL);
            cpData.setStateTimer(0);

            occupations.clear(); // 清空占用队列
            var maxCP = getMaxCP(player.getUUID());
            cpData.setAvailableCP(maxCP, maxCP); // 恢复 CP

            NeoForge.EVENT_BUS.post(new AbilityRecoveryEvent(player));
            return true;
        }
        return false;
    }

    private boolean tickSpRegen(ServerPlayer player, AbilityData cpData) {
        if (cpData.getCurrSP() >= cpData.getMaxSP()) return false;
        if (player.getFoodData().getSaturationLevel() <= 0) return false;
        if (cpData.getCurrSP() == 0) return false;

        return cpData.tickSpRegenTimer();
    }

    private boolean processOccupations(ServerPlayer player, AbilityData cpData, List<AbilityData.CpOccupationData> occupations) {
        var hasTimedOccupation = occupations.stream().anyMatch(occupation -> !occupation.isPermanent());
        if (!hasTimedOccupation) {
            cpIterationProgress.remove(player.getUUID());
            return false;
        }

        var recoveryRate = getCpIterationRate(isSkillDebugMode(player.getUUID()))
                * PlayerAttributeRuntime.neuralIterationMultiplier(player)
                * (1.0f + getBonuses(player.getUUID()).recovery());
        var progress = cpIterationProgress.merge(player.getUUID(), recoveryRate, Float::sum);
        var recoverySteps = (int) Math.floor(progress / TICKS_PER_ITERATION_POINT);
        if (recoverySteps > 0) {
            cpIterationProgress.put(
                    player.getUUID(),
                    progress - recoverySteps * TICKS_PER_ITERATION_POINT
            );
        }

        var dirty = false;
        var it = occupations.iterator();
        while (it.hasNext()) {
            var occupation = it.next();

            // 永久占用，跳过迭代
            if (occupation.isPermanent()) continue;

            // cp迭代
            if (cpData.getStatus() != AbilityData.Status.OVERLOAD && recoverySteps > 0) {
                occupation.setIterationTicks(Math.max(
                        occupation.getIterationTicks() - recoverySteps,
                        0
                ));
                dirty = true;
            }

            if (!occupation.isFree()) continue;
            //sp消耗 =（cp迭代量*系数X）* 50% * sp消耗减少率
            var spReductionRate = AbilitySystemServer.getSPReductionRate(player);
            var spCost = (int) (occupation.getAmount() * DAMAGE_MULTIPLIER * 0.5f * spReductionRate);
            if (!isSkillDebugMode(player.getUUID())) {
                if (cpData.getCurrSP() < spCost) continue;
                cpData.addSP(-spCost);
            }

            // 归还迭代完成的CP占用
            cpData.setAvailableCP(
                    cpData.getAvailableCP() + occupation.getAmount(),
                    getMaxCP(player.getUUID())
            );
            it.remove();
            dirty = true;
        }
        return dirty;
    }

    public boolean isSkillDebugMode(UUID uuid) {
        return skillDebugPlayers.contains(uuid);
    }

    public boolean toggleSkillDebugMode(UUID uuid) {
        if (skillDebugPlayers.remove(uuid)) return false;
        skillDebugPlayers.add(uuid);
        return true;
    }

    static boolean isAutomaticSkillDebugPlayer(String playerName) {
        return "Dev".equals(playerName) || "Dusk_ark".equals(playerName);
    }

    static int getCpIterationRate(boolean skillDebugMode) {
        return skillDebugMode ? 5 : 1;
    }

    public boolean tryOccupation(UUID uuid, float amount, Skill skill, int iterationTicks, boolean isPermanent) {
        var playerData = playerDataManager.getData(uuid);
        if (playerData == null) return false;

        var cpData = playerData.getCpData();
        var occupations = playerData.getCpOccupations();
        var skillData = playerData.getSkillDataMap().get(skill.getKeyString());
        var level = (skillData != null) ? skillData.getLevel() : 0;

        if (cpData.getStatus() == AbilityData.Status.OVERLOAD) return false;
        if (cpData.getAvailableCP() < amount) return false;

        if (!isPermanent && skill.getMaxStacks(level) != Skill.NO_STACK_LIMIT) {
            var currentStacks = occupations.stream()
                    .filter(occupation -> !occupation.isPermanent())
                    .filter(occ -> skill.getKeyString().equals(occ.getSkillId()))
                    .count();
            if (currentStacks >= skill.getMaxStacks(level)) return false;
        }

        if (amount <= 0) return true;

        var effectiveIterationTicks = iterationTicks;
        if (!isPermanent && effectiveIterationTicks <= 0) {
            effectiveIterationTicks = Math.max(1, (int) Math.ceil(amount * 0.5f));
        }
        occupations.add(new AbilityData.CpOccupationData(
                amount,
                effectiveIterationTicks,
                skill.getKeyString(),
                isPermanent
        ));
        cpData.setAvailableCP(cpData.getAvailableCP() - amount, getMaxCP(uuid));
        return true;
    }

    public void releaseMaintenanceOccupation(UUID uuid, String skillId) {
        modify(uuid, cpData -> {
            var playerData = playerDataManager.getData(uuid);
            if (playerData == null) return;
            var occupations = playerData.getCpOccupations();

            var it = occupations.iterator();
            while (it.hasNext()) {
                var occ = it.next();
                if (occ.isPermanent() && skillId.equals(occ.getSkillId())) {
                    cpData.setAvailableCP(cpData.getAvailableCP() + occ.getAmount(), getMaxCP(uuid));
                    it.remove();
                }
            }
        });
    }

    public void releaseAllOccupations(UUID uuid) {
        modify(uuid, cpData -> {
            var playerData = playerDataManager.getData(uuid);
            if (playerData == null) return;
            var occupations = playerData.getCpOccupations();
            occupations.clear();
            cpIterationProgress.remove(uuid);
            var maxCP = getMaxCP(uuid);
            cpData.setAvailableCP(maxCP, maxCP);
        });
        syncManager.schedulePlayerSync(uuid, SyncTypes.CP_DATA);
    }

    public boolean ensurePermanentOccupation(UUID uuid, float amount, Skill skill) {
        var playerData = playerDataManager.getData(uuid);
        if (playerData == null) return false;

        var matching = playerData.getCpOccupations().stream()
                .filter(occupation -> occupation.isPermanent()
                        && skill.getKeyString().equals(occupation.getSkillId()))
                .toList();
        if (matching.size() == 1 && Float.compare(matching.getFirst().getAmount(), amount) == 0) {
            return true;
        }

        releaseMaintenanceOccupation(uuid, skill.getKeyString());
        return tryOccupation(uuid, amount, skill, 0, true);
    }

    /**
     * Atomically replaces the permanent occupations for the supplied skills and optionally adds one
     * timed occupation. Existing permanent reservations remain untouched when the complete change
     * cannot be afforded.
     */
    public boolean replacePermanentOccupationsAndTryOccupation(
            UUID uuid,
            Map<Skill, Float> permanentAmounts,
            Skill timedSkill,
            float timedAmount,
            int iterationTicks
    ) {
        var plan = createAtomicOccupationPlan(
                uuid,
                permanentAmounts,
                timedSkill,
                timedAmount,
                iterationTicks
        );
        if (plan == null) return false;

        var playerData = plan.playerData();
        var cpData = playerData.getCpData();
        var occupations = playerData.getCpOccupations();
        occupations.removeIf(occupation -> occupation.isPermanent()
                && plan.replacementIds().contains(occupation.getSkillId()));
        for (var entry : plan.permanentAmounts().entrySet()) {
            if (entry.getValue() <= 0) continue;
            occupations.add(new AbilityData.CpOccupationData(
                    entry.getValue(),
                    0,
                    entry.getKey().getKeyString(),
                    true
            ));
        }
        if (plan.timedAmount() > 0) {
            occupations.add(new AbilityData.CpOccupationData(
                    plan.timedAmount(),
                    plan.iterationTicks(),
                    plan.timedSkill().getKeyString(),
                    false
            ));
        }
        cpData.setAvailableCP(plan.availableAfterRelease() - plan.totalRequired(), getMaxCP(uuid));
        playerData.markDirty();
        syncManager.schedulePlayerSync(uuid, SyncTypes.CP_DATA);
        return true;
    }

    public boolean canReplacePermanentOccupationsAndTryOccupation(
            UUID uuid,
            Map<Skill, Float> permanentAmounts,
            Skill timedSkill,
            float timedAmount,
            int iterationTicks
    ) {
        return createAtomicOccupationPlan(
                uuid,
                permanentAmounts,
                timedSkill,
                timedAmount,
                iterationTicks
        ) != null;
    }

    private AtomicOccupationPlan createAtomicOccupationPlan(
            UUID uuid,
            Map<Skill, Float> permanentAmounts,
            Skill timedSkill,
            float timedAmount,
            int iterationTicks
    ) {
        var playerData = playerDataManager.getData(uuid);
        if (playerData == null || permanentAmounts == null) return null;

        var cpData = playerData.getCpData();
        if (cpData.getStatus() == AbilityData.Status.OVERLOAD) return null;
        if (!Float.isFinite(timedAmount) || timedAmount < 0) return null;
        if (timedAmount > 0 && timedSkill == null) return null;

        var replacementIds = new java.util.HashSet<String>();
        var replacementTotal = 0.0f;
        for (var entry : permanentAmounts.entrySet()) {
            var skill = entry.getKey();
            var amount = entry.getValue();
            if (skill == null || amount == null || !Float.isFinite(amount) || amount < 0) return null;
            if (!replacementIds.add(skill.getKeyString())) return null;
            replacementTotal += amount;
            if (!Float.isFinite(replacementTotal)) return null;
        }

        var occupations = playerData.getCpOccupations();
        if (timedAmount > 0) {
            var maxStacks = timedSkill.getMaxStacks(getSkillLevel(playerData, timedSkill));
            if (maxStacks != Skill.NO_STACK_LIMIT) {
                var currentStacks = occupations.stream()
                        .filter(occupation -> !occupation.isPermanent())
                        .filter(occupation -> timedSkill.getKeyString().equals(occupation.getSkillId()))
                        .count();
                if (currentStacks >= maxStacks) return null;
            }
        }

        var released = 0.0f;
        for (var occupation : occupations) {
            if (occupation.isPermanent() && replacementIds.contains(occupation.getSkillId())) {
                released += occupation.getAmount();
            }
        }
        var totalRequired = replacementTotal + timedAmount;
        if (!Float.isFinite(totalRequired)
                || !isAtomicReplacementAffordable(cpData.getAvailableCP(), released, totalRequired)) {
            return null;
        }
        var effectiveIterationTicks = timedAmount <= 0
                ? 0
                : iterationTicks > 0
                ? iterationTicks
                : Math.max(1, (int) Math.ceil(timedAmount * 0.5f));
        return new AtomicOccupationPlan(
                playerData,
                Map.copyOf(permanentAmounts),
                Set.copyOf(replacementIds),
                timedSkill,
                timedAmount,
                effectiveIterationTicks,
                cpData.getAvailableCP() + released,
                totalRequired
        );
    }

    public boolean tryOccupationIf(
            UUID uuid,
            float amount,
            Skill skill,
            int iterationTicks,
            BooleanSupplier action
    ) {
        var playerData = playerDataManager.getData(uuid);
        if (playerData == null || action == null || !Float.isFinite(amount) || amount < 0) return false;
        var cpData = playerData.getCpData();
        if (cpData.getStatus() == AbilityData.Status.OVERLOAD || cpData.getAvailableCP() < amount) return false;

        var skillData = playerData.getSkillDataMap().get(skill.getKeyString());
        var level = skillData == null ? 0 : skillData.getLevel();
        if (skill.getMaxStacks(level) != Skill.NO_STACK_LIMIT) {
            var currentStacks = playerData.getCpOccupations().stream()
                    .filter(occupation -> !occupation.isPermanent())
                    .filter(occupation -> skill.getKeyString().equals(occupation.getSkillId()))
                    .count();
            if (currentStacks >= skill.getMaxStacks(level)) return false;
        }
        if (!action.getAsBoolean()) return false;
        return tryOccupation(uuid, amount, skill, iterationTicks, false);
    }

    static boolean isAtomicReplacementAffordable(float available, float released, float required) {
        if (!Float.isFinite(available)
                || !Float.isFinite(released) || released < 0
                || !Float.isFinite(required) || required < 0) {
            return false;
        }
        if (required <= released) return true;
        var additionalRequired = required - released;
        return Float.isFinite(additionalRequired) && available >= additionalRequired;
    }

    private static int getSkillLevel(
            org.academy.internal.server.world.level.storage.Player playerData,
            Skill skill
    ) {
        var data = playerData.getSkillDataMap().get(skill.getKeyString());
        return data == null ? 0 : data.getLevel();
    }

    private record AtomicOccupationPlan(
            org.academy.internal.server.world.level.storage.Player playerData,
            Map<Skill, Float> permanentAmounts,
            Set<String> replacementIds,
            Skill timedSkill,
            float timedAmount,
            int iterationTicks,
            float availableAfterRelease,
            float totalRequired
    ) {
    }


    @SubscribeEvent
    public void onPlayerEat(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer player && !player.level().isClientSide()) {
            var itemStack = event.getItem();
            if (itemStack.has(DataComponents.FOOD)) {
                var food = itemStack.get(DataComponents.FOOD);
                if (food != null) {
                    var saturationGained = food.nutrition() * food.saturation() * 2.0f;
                    if (saturationGained > 0) {
                        var spRecovery = (int) (saturationGained * 5);
                        addCurrSP(player.getUUID(), spRecovery);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onSleepFinishedTimeEvent(SleepFinishedTimeEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            for (var player : level.players()) {
                modify(player.getUUID(), data -> data.setCurrSP(data.getMaxSP()));
            }
        }
    }

    @SubscribeEvent
    public void onAdvancementEarn(AdvancementEvent.AdvancementEarnEvent event) {
        var display = event.getAdvancement().value().display().orElse(null);
        if (display != null && display.getType() == AdvancementType.CHALLENGE) {
            var uuid = event.getEntity().getUUID();
            setMaxCP(uuid, getMaxCP(uuid) + 5f);
        }
    }

    private void modify(UUID uuid, Consumer<AbilityData> action) {
        var playerData = playerDataManager.getData(uuid);
        if (playerData != null) {
            var cpData = playerData.getCpData();
            action.accept(cpData);
            if (cpData.isDirty()) {
                playerData.markDirty();
            }
        }
    }

    private <T> T query(UUID uuid, Function<AbilityData, T> mapper, T defaultValue) {
        var playerData = playerDataManager.getData(uuid);
        if (playerData == null) {
            return defaultValue;
        }
        return mapper.apply(playerData.getCpData());
    }

    public int getLevel(UUID uuid) {
        return query(uuid, AbilityData::getLevel, AbilityLevel.LEVEL0).getLevelCode();
    }

    public void setLevel(UUID uuid, int levelCode) {
        modify(uuid, cpData -> {
            cpData.setLevel(AbilityLevel.fromLevelCode(levelCode));
            cpData.setAbilityExp(0);
        });
    }

    public float getLevelBasicCP(int levelCode) {
        return AbilityLevel.values()[levelCode].getBasicCP();
    }

    public void setMaxCP(UUID uuid, float newMaxCP) {
        modify(uuid, cpData -> {
            var requestedMaxCP = Float.isFinite(newMaxCP) ? Math.max(0, newMaxCP) : 0;
            var bonus = getBonuses(uuid).maxCp();
            var safeMaxCP = Math.max(bonus, requestedMaxCP);
            var oldEffectiveMaxCP = getMaxCP(uuid);
            if (Float.compare(oldEffectiveMaxCP, safeMaxCP) == 0) return;

            var newBaseMaxCP = Math.max(0, safeMaxCP - bonus);
            var diff = safeMaxCP - oldEffectiveMaxCP;
            var newAvailableCP = cpData.getAvailableCP() + diff;

            cpData.setMaxCP(newBaseMaxCP);
            cpData.setAvailableCP(newAvailableCP, safeMaxCP);
            checkAndUpgradeLevel(cpData);
        });
    }

    private void checkAndUpgradeLevel(AbilityData cpData) {
        var currentMaxCP = cpData.getMaxCP();
        var newLevel = AbilityLevel.LEVEL0;

        for (var i = CACHED_LEVELS.length - 1; i >= 0; i--) {
            var lvl = CACHED_LEVELS[i];
            if (currentMaxCP >= lvl.getBasicCP() - CP_RATING_OFFSET) {
                newLevel = lvl;
                break;
            }
        }

        if (newLevel != cpData.getLevel()) {
            LOGGER.info("Player Level Changed: {} -> {} (MaxCP: {})", cpData.getLevel(), newLevel, currentMaxCP);
            cpData.setLevel(newLevel);
        }
    }

    public float getAbilityExp(UUID uuid) {
        return query(uuid, AbilityData::getAbilityExp, 0f);
    }

    public void setAbilityExp(UUID uuid, float amount) {
        modify(uuid, cpData -> cpData.setAbilityExp(amount));
    }

    private float getAbilityExpMax(UUID uuid) {
        var playerData = playerDataManager.getData(uuid);
        if (playerData == null) return 1f;

        var categoryId = playerData.getAbilityCategory();

        var categoryRef = Registries.ABILITY_CATEGORIES.get(Identifier.parse(categoryId));
        if (categoryRef.isEmpty()) return 1f;

        var category = categoryRef.get().value();
        var level = getLevel(uuid);
        var count = category.getSkills().stream()
                .filter(skill -> skill.getRecommendedLevel().getLevelCode() == level)
                .count();
        return count > 0 ? count * 1000f : 1000f;
    }

    public boolean canLevelUp(UUID uuid) {
        return getLevel(uuid) < 5 && getAbilityExp(uuid) >= 1f;
    }

    public void addLevelProgress(UUID uuid, float amount) {
        modify(uuid, cpData -> {
            var playerData = playerDataManager.getData(uuid);
            if (playerData == null) return;

            var mul = 1.0f;
            var categoryId = playerData.getAbilityCategory();
            var categoryRef = Registries.ABILITY_CATEGORIES.get(Identifier.parse(categoryId));
            if (categoryRef.isPresent()) {
                mul = categoryRef.get().value().getProgIncrRate();
            }
            cpData.addAbilityExp(amount * mul);
        });
    }

    public float getMaxCP(UUID uuid) {
        var base = query(uuid, AbilityData::getMaxCP, 1f);
        return base + getBonuses(uuid).maxCp();
    }

    public float getAvailableCP(UUID uuid) {
        return query(uuid, AbilityData::getAvailableCP, 0f);
    }

    public void setAvailableCP(UUID uuid, float availableCP) {
        var safeCP = Float.isFinite(availableCP) ? availableCP : 0f;
        modify(uuid, cpData -> {
            if (Float.compare(cpData.getAvailableCP(), safeCP) != 0) {
                cpData.setAvailableCP(safeCP, getMaxCP(uuid));
            }
        });
    }

    public float getOccupiedCP(UUID uuid) {
        return getMaxCP(uuid) - getAvailableCP(uuid);
    }

    public AbilityData.Status getStatus(UUID uuid) {
        return query(uuid, AbilityData::getStatus, AbilityData.Status.NORMAL);
    }

    public void setStatus(UUID uuid, AbilityData.Status status) {
        modify(uuid, cpData -> cpData.setStatus(status));
    }

    public int getStateTimer(UUID uuid) {
        return query(uuid, AbilityData::getStateTimer, 0);
    }

    public void setStateTimer(UUID uuid, int stateTimer) {
        modify(uuid, cpData -> cpData.setStateTimer(stateTimer));
    }

    public int getCurrSP(UUID uuid) {
        return query(uuid, AbilityData::getCurrSP, 0);
    }

    public void setCurrSP(UUID uuid, int currSP) {
        modify(uuid, cpData -> cpData.setCurrSP(currSP));
    }

    public void addCurrSP(UUID uuid, int addSP) {
        modify(uuid, cpData -> cpData.addSP(addSP));
    }

    public int getMaxSP(UUID uuid) {
        return query(uuid, AbilityData::getMaxSP, 0);
    }

    public void setMaxSP(UUID uuid, int maxSP) {
        modify(uuid, cpData -> cpData.setMaxSP(maxSP));
    }

    public float getFreeCPRatio(UUID uuid) {
        var maxCP = getMaxCP(uuid);
        if (maxCP <= 0) return 0f;
        return getAvailableCP(uuid) / maxCP;
    }

    public float getDamageMultiplier(UUID uuid) {
        var ratio = Math.clamp(getFreeCPRatio(uuid), 0, 1);
        var cpMultiplier = ratio >= 0.5f
                ? 1.0f
                : 0.25f + (ratio / 0.5f) * 0.75f;
        return cpMultiplier * getAbilityPowerMultiplier(uuid);
    }

    public float getCalculationEfficiency(UUID uuid) {
        return getBonuses(uuid).efficiency();
    }

    public float getCalculationIntensity(UUID uuid) {
        return 1.0f / (1.0f + getCalculationEfficiency(uuid) * 2.0f);
    }

    public float getAbilityPowerMultiplier(UUID uuid) {
        return 1.0f + getCalculationEfficiency(uuid);
    }

    public void refreshCommonSkillBonuses(UUID uuid) {
        var playerData = playerDataManager.getData(uuid);
        if (playerData == null) return;

        var desiredBonus = getBonuses(uuid).maxCp();
        var appliedBonus = playerData.getAppliedCommonSkillMaxCpBonus();
        if (Float.compare(desiredBonus, appliedBonus) == 0) return;

        var effectiveMaxCP = playerData.getCpData().getMaxCP() + desiredBonus;
        var diff = desiredBonus - appliedBonus;
        var cpData = playerData.getCpData();
        cpData.setAvailableCP(cpData.getAvailableCP() + diff, effectiveMaxCP);
        playerData.setAppliedCommonSkillMaxCpBonus(desiredBonus);
        syncManager.schedulePlayerSync(uuid, SyncTypes.CP_DATA);
    }

    private BrainDevelopmentBonuses.Bonuses getBonuses(UUID uuid) {
        var playerData = playerDataManager.getData(uuid);
        if (playerData == null) return BrainDevelopmentBonuses.NONE;

        var category = playerDataManager.getPlayerAbilityCategory(uuid);
        return BrainDevelopmentBonuses.calculate(
                playerData.getSkillDataMap().keySet(),
                brainDevelopmentSettings,
                category.supportsCommonSkills()
        );
    }

    public float getRangeMultiplier(UUID uuid) {
        var ratio = Math.clamp(getFreeCPRatio(uuid), 0, 1);
        if (ratio >= 0.5f) return 1.0f;
        return 0.50f + (ratio / 0.5f) * 0.50f;
    }

    public float getEffectiveDistanceMultiplier(UUID uuid) {
        var ratio = Math.clamp(getFreeCPRatio(uuid), 0, 1);
        if (ratio >= 0.5f) return 1.0f;
        return 0.40f + (ratio / 0.5f) * 0.60f;
    }

    public float getCurrMP(UUID uuid) {
        return query(uuid, AbilityData::getCurrMP, 0f);
    }

    public void setCurrMP(UUID uuid, float currMP) {
        modify(uuid, cpData -> cpData.setCurrMP(currMP));
    }

    public void addCurrMP(UUID uuid, float addMP) {
        modify(uuid, cpData -> cpData.addMP(addMP));
    }

    public float getMaxMP(UUID uuid) {
        return query(uuid, AbilityData::getMaxMP, 0f);
    }

    public void setMaxMP(UUID uuid, float maxMP) {
        modify(uuid, cpData -> cpData.setMaxMP(maxMP));
    }

    public boolean tryConsumeMP(UUID uuid, float amount) {
        var currMP = getCurrMP(uuid);
        if (currMP < amount) return false;
        setCurrMP(uuid, currMP - amount);
        return true;
    }
}
