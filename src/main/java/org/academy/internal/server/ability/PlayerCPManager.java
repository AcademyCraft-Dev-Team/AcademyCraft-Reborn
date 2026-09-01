package org.academy.internal.server.ability;

import net.minecraft.advancements.AdvancementType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.LearningHelper;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.ability.SyncTypes;
import org.academy.api.common.ability.event.AbilityOverloadEvent;
import org.academy.api.common.ability.event.AbilityRecoveryEvent;
import org.academy.api.common.ability.pakcet.SyncAbilityDataPacket;
import org.academy.api.common.data.AbilityData;
import org.academy.api.common.registries.Registries;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.attribute.PlayerAttributeRuntime;
import org.academy.internal.common.world.level.block.AbilityDeveloperSleep;
import org.academy.internal.server.config.AbilityConfig;
import org.academy.internal.server.world.level.storage.Player;
import org.misaka.MisakaNetworkServer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

public class PlayerCPManager implements AbilitySubsystem {
    private static final StackWalker STATE_STACK_WALKER = StackWalker.getInstance(
            StackWalker.Option.RETAIN_CLASS_REFERENCE
    );
    static final float BASE_MAX_CP = 100.0f;
    static final float MAX_SKILL_PROFICIENCY_CP_BONUS = 300.0f;
    static final float MAX_CHALLENGE_CP_BONUS = 200.0f;
    static final int OVERLOAD_TICKS = 200;
    static final float RECOVERED_CP_PER_SP = 10.0f;
    private static final float TICKS_PER_ITERATION_POINT = 20.0f;
    private static final int FOOD_SP_RECOVERY_TICKS_PER_NUTRITION = 20 * 10;
    private static final float CP_EPSILON = 1.0e-4f;

    private final PlayerDataManager playerDataManager;
    private final SyncManager syncManager;
    private final Set<UUID> skillDebugPlayers = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, Float> cpIterationProgress = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Float> debugMaxCpOverrides = new ConcurrentHashMap<>();

    public PlayerCPManager(PlayerDataManager manager, AbilityConfig config, SyncManager syncManager) {
        playerDataManager = manager;
        this.syncManager = syncManager;

    }

    private static void enterOverload(AbilityData cpData, ServerPlayer player) {
        cpData.setStatus(AbilityData.Status.OVERLOAD);
        cpData.setStateTimer(OVERLOAD_TICKS);
        NeoForge.EVENT_BUS.post(new AbilityOverloadEvent(player));
    }

    /**
     * Advances every timed stack independently so one cast never waits for earlier stacks.
     */
    static boolean advanceTimedOccupationIterations(
            List<AbilityData.CpOccupationData> occupations,
            int recoverySteps
    ) {
        if (occupations == null || occupations.isEmpty() || recoverySteps <= 0) return false;
        var changed = false;
        for (var occupation : occupations) {
            if (occupation == null || occupation.isPermanent()) continue;
            var previous = occupation.getIterationTicks();
            var next = Math.max(previous - recoverySteps, 0);
            if (next == previous) continue;
            occupation.setIterationTicks(next);
            changed = true;
        }
        return changed;
    }

    static CpRecoveryPlan planCpRecovery(float requestedCp, float remainderCp, int currentSp) {
        return planCpRecovery(requestedCp, remainderCp, currentSp, RECOVERED_CP_PER_SP);
    }

    static CpRecoveryPlan planCpRecovery(float requestedCp, float remainderCp, int currentSp,
                                         float recoveredCpPerSp) {
        var safeRecoveredCpPerSp = Float.isFinite(recoveredCpPerSp) && recoveredCpPerSp > 0.0f
                ? recoveredCpPerSp : RECOVERED_CP_PER_SP;
        if (!Float.isFinite(requestedCp) || requestedCp <= 0.0f || currentSp <= 0) {
            return new CpRecoveryPlan(
                    0.0f,
                    normalizeRecoveryRemainder(remainderCp, safeRecoveredCpPerSp),
                    0
            );
        }
        var normalizedRemainder = normalizeRecoveryRemainder(remainderCp, safeRecoveredCpPerSp);
        var recoverableBeforeEmpty = currentSp * safeRecoveredCpPerSp - normalizedRemainder;
        var recovered = Math.min(requestedCp, Math.max(0.0f, recoverableBeforeEmpty));
        if (recovered <= CP_EPSILON) {
            return new CpRecoveryPlan(0.0f, normalizedRemainder, 0);
        }

        var accumulated = normalizedRemainder + recovered;
        var spCost = Math.min(currentSp,
                Mth.floor((accumulated + CP_EPSILON) / safeRecoveredCpPerSp));
        var nextRemainder = accumulated - spCost * safeRecoveredCpPerSp;
        if (nextRemainder < CP_EPSILON) nextRemainder = 0.0f;
        return new CpRecoveryPlan(
                recovered,
                normalizeRecoveryRemainder(nextRemainder, safeRecoveredCpPerSp),
                spCost
        );
    }

    private static float normalizeRecoveryRemainder(float remainderCp, float recoveredCpPerSp) {
        if (!Float.isFinite(remainderCp) || remainderCp <= 0.0f) return 0.0f;
        var normalized = remainderCp % recoveredCpPerSp;
        return normalized < CP_EPSILON ? 0.0f : normalized;
    }

    static boolean isAutomaticSkillDebugPlayer(String playerName) {
        return "Dev".equals(playerName) || "Dusk_ark".equals(playerName);
    }

    static int getCpIterationRate(boolean skillDebugMode) {
        return skillDebugMode ? 5 : 1;
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
            Player playerData,
            Skill skill
    ) {
        var data = playerData.getSkillDataMap().get(skill.getKeyString());
        return data == null ? 0 : skill.getLevelForProficiency(data.getProficiency());
    }

    static int foodSpRecoveryDurationTicks(int nutrition) {
        return (int) Math.min(
                Integer.MAX_VALUE,
                (long) Math.max(0, nutrition) * FOOD_SP_RECOVERY_TICKS_PER_NUTRITION
        );
    }

    static float normalizeDebugMaxCP(float maxCP) {
        return Float.isFinite(maxCP) ? Math.max(0.0f, maxCP) : 0.0f;
    }

    static float resolveEffectiveMaxCP(float naturalMaxCP, Float debugOverride) {
        return debugOverride == null
                ? normalizeDebugMaxCP(naturalMaxCP)
                : normalizeDebugMaxCP(debugOverride);
    }

    static float calculationEfficiency(float maxCp) {
        return Float.isFinite(maxCp) ? Math.max(0.0f, maxCp) * 0.0005f : 0.0f;
    }

    static float abilityLevelCpBonus(int level) {
        if (level >= 5) return 300.0f;
        if (level == 4) return 140.0f;
        if (level == 3) return 60.0f;
        if (level == 2) return 20.0f;
        return 0.0f;
    }

    static float initialPersistentMaxCp(float storedMaxCp, float derivedBonus) {
        var safeStored = normalizeDebugMaxCP(storedMaxCp);
        var safeDerived = normalizeDebugMaxCP(derivedBonus);
        return Math.max(safeStored, BASE_MAX_CP + safeDerived);
    }

    static float applyDerivedMaxCpGrowth(float storedMaxCp, float appliedBonus, float desiredBonus) {
        var safeStored = normalizeDebugMaxCP(storedMaxCp);
        var safeApplied = normalizeDebugMaxCP(appliedBonus);
        var safeDesired = normalizeDebugMaxCP(desiredBonus);
        return Math.max(BASE_MAX_CP, safeStored + Math.max(0.0f, safeDesired - safeApplied));
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
        var occupations = playerData.getMutableCpOccupations();

        var dirty = false;

        var bonuses = getBonuses(player.getUUID());
        if (bonuses.overloadImmune() && cpData.getStatus() != AbilityData.Status.NORMAL) {
            cpData.setStatus(AbilityData.Status.NORMAL);
            cpData.setStateTimer(0);
            dirty = true;
        }

        dirty |= switch (cpData.getStatus()) {
            case NORMAL -> tickNormal(cpData, player, bonuses.overloadImmune());
            case PERSONAL_REALITY_OVERLOAD -> tickWarning(cpData, player, bonuses.overloadImmune());
            case OVERLOAD -> tickOverload(cpData, occupations, player);
        };

        if (cpData.getStatus() != AbilityData.Status.OVERLOAD) {
            dirty |= processOccupations(player, cpData, occupations);
        }

        dirty |= cpData.tickFoodSpRecovery();

        AbilityDeveloperSleep.refreshNightSleepStatus(player);
        if (AbilityDeveloperSleep.shouldRecoverSp(player)
                && cpData.getCurrSP() < cpData.getMaxSP()) {
            cpData.addSP(1);
            dirty = true;
        }

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
                cpData.copyWithMaxCP(getMaxCP(serverPlayer.getUUID())),
                getCalculationIntensity(serverPlayer.getUUID()),
                player.getMutableCpOccupations()
        ));
        cpData.clearDirty();
    }

    private boolean tickNormal(AbilityData cpData, ServerPlayer player, boolean overloadImmune) {
        if (overloadImmune || cpData.getAvailableCP() > 0.0f) return false;
        enterOverload(cpData, player);
        return true;
    }

    private boolean tickWarning(AbilityData cpData, ServerPlayer player, boolean overloadImmune) {
        if (overloadImmune) {
            cpData.setStatus(AbilityData.Status.NORMAL);
            cpData.setStateTimer(0);
            return true;
        }
        enterOverload(cpData, player);
        return true;
    }

    private boolean enterOverloadIfDepleted(UUID uuid, AbilityData cpData) {
        if (cpData.getAvailableCP() > 0.0f
                || cpData.getStatus() == AbilityData.Status.OVERLOAD
                || getBonuses(uuid).overloadImmune()) {
            return false;
        }
        cpData.setStatus(AbilityData.Status.OVERLOAD);
        cpData.setStateTimer(OVERLOAD_TICKS);
        var player = syncManager.getOnlinePlayer(uuid);
        if (player != null) NeoForge.EVENT_BUS.post(new AbilityOverloadEvent(player));
        syncManager.schedulePlayerSync(uuid, SyncTypes.CP_DATA);
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

    private boolean processOccupations(ServerPlayer player, AbilityData cpData, List<AbilityData.CpOccupationData> occupations) {
        var dirty = releaseInactiveMaintenanceOccupations(player, cpData, occupations);
        var hasTimedOccupation = occupations.stream().anyMatch(occupation -> !occupation.isPermanent());
        if (!hasTimedOccupation) {
            cpIterationProgress.remove(player.getUUID());
            return dirty;
        }

        var debugMode = isSkillDebugMode(player.getUUID());
        var recoverySteps = 0;
        if (debugMode || cpData.getCurrSP() > 0) {
            var recoveryRate = (float) (getCpIterationRate(debugMode)
                    * PlayerAttributeRuntime.neuralIterationMultiplier(player)
                    * getBonuses(player.getUUID()).iterationMultiplier());
            var progress = cpIterationProgress.merge(player.getUUID(), recoveryRate, Float::sum);
            recoverySteps = Mth.floor(progress / TICKS_PER_ITERATION_POINT);
            if (recoverySteps > 0) {
                cpIterationProgress.put(
                        player.getUUID(),
                        progress - recoverySteps * TICKS_PER_ITERATION_POINT
                );
            }
        }

        if (cpData.getStatus() != AbilityData.Status.OVERLOAD && recoverySteps > 0) {
            dirty |= advanceTimedOccupationIterations(occupations, recoverySteps);
        }

        var it = occupations.iterator();
        while (it.hasNext()) {
            var occupation = it.next();

            if (occupation.isPermanent()) continue;
            if (!occupation.isFree()) continue;
            var recovered = occupation.getAmount();
            if (!debugMode) {
                var plan = planCpRecovery(
                        recovered,
                        cpData.getSpRecoveryCpRemainder(),
                        cpData.getCurrSP(),
                        getBonuses(player.getUUID()).recoveredCpPerSp()
                );
                recovered = plan.recoveredCp();
                if (recovered <= CP_EPSILON) continue;
                cpData.setSpRecoveryCpRemainder(plan.remainderCp());
                if (plan.spCost() > 0) cpData.addSP(-plan.spCost());
            }

            cpData.setAvailableCP(
                    cpData.getAvailableCP() + recovered,
                    getMaxCP(player.getUUID())
            );
            var remaining = occupation.getAmount() - recovered;
            if (remaining <= CP_EPSILON) it.remove();
            else occupation.setAmount(remaining);
            dirty = true;
        }
        return dirty;
    }

    private boolean releaseInactiveMaintenanceOccupations(
            ServerPlayer player,
            AbilityData cpData,
            List<AbilityData.CpOccupationData> occupations
    ) {
        var released = 0.0f;
        var iterator = occupations.iterator();
        while (iterator.hasNext()) {
            var occupation = iterator.next();
            if (!occupation.isPermanent()) continue;
            if ("academy:darkmatter_generation".equals(occupation.getSkillId())
                    && cpData.getCurrMP() > CP_EPSILON) continue;
            var id = Identifier.tryParse(occupation.getSkillId());
            var enabled = id != null && Registries.SKILLS.get(id)
                    .map(reference -> reference.value().isEnabled(player))
                    .orElse(false);
            if (enabled) continue;
            released += occupation.getAmount();
            iterator.remove();
        }
        if (released <= 0.0f) return false;
        cpData.setAvailableCP(cpData.getAvailableCP() + released, getMaxCP(player.getUUID()));
        return true;
    }

    public boolean isSkillDebugMode(UUID uuid) {
        return skillDebugPlayers.contains(uuid);
    }

    public boolean toggleSkillDebugMode(UUID uuid) {
        if (skillDebugPlayers.remove(uuid)) return false;
        skillDebugPlayers.add(uuid);
        return true;
    }

    public boolean tryOccupation(UUID uuid, float amount, Skill skill, int iterationTicks, boolean isPermanent) {
        return tryOccupation(uuid, amount, skill, iterationTicks, isPermanent, null, Skill.NO_STACK_LIMIT);
    }

    public boolean tryOccupation(UUID uuid, float amount, Skill skill, int iterationTicks,
                                 boolean isPermanent, String stackGroup, int stackLimit) {
        return tryOccupationAndConsumeMP(
                uuid, amount, 0.0f, skill, iterationTicks, isPermanent, stackGroup, stackLimit);
    }

    /** Atomically reserves CP and consumes MP, or changes neither resource. */
    public boolean tryOccupationAndConsumeMP(
            UUID uuid,
            float cpAmount,
            float mpAmount,
            Skill skill,
            int iterationTicks,
            boolean isPermanent,
            String stackGroup,
            int stackLimit
    ) {
        var playerData = playerDataManager.getData(uuid);
        if (playerData == null || skill == null
                || !Float.isFinite(cpAmount) || cpAmount < 0.0f
                || !Float.isFinite(mpAmount) || mpAmount < 0.0f) return false;

        var cpData = playerData.getCpData();
        var occupations = playerData.getMutableCpOccupations();
        var skillData = playerData.getSkillDataMap().get(skill.getKeyString());
        var level = (skillData != null) ? skill.getLevelForProficiency(skillData.getProficiency()) : 0;

        if (cpData.getStatus() == AbilityData.Status.OVERLOAD) return false;
        if (enterOverloadIfDepleted(uuid, cpData)) return false;
        if (!canAffordCombinedCost(
                cpData.getAvailableCP(), cpData.getCurrMP(), cpAmount, mpAmount)) return false;

        var hasDedicatedStackGroup = stackGroup != null && !stackGroup.isBlank();
        var normalizedStackGroup = hasDedicatedStackGroup ? stackGroup : skill.getKeyString();
        var maxStacks = hasDedicatedStackGroup
                ? (stackLimit == Skill.NO_STACK_LIMIT ? Skill.NO_STACK_LIMIT : Math.max(0, stackLimit))
                : getMaxStacks(uuid, skill, level);
        if (!isPermanent && maxStacks != Skill.NO_STACK_LIMIT) {
            var currentStacks = countTimedStacks(occupations, normalizedStackGroup);
            if (currentStacks >= maxStacks) return false;
        }

        if (cpAmount <= 0.0f && mpAmount <= 0.0f) return true;

        var effectiveIterationTicks = iterationTicks;
        if (cpAmount > 0.0f && !isPermanent && effectiveIterationTicks <= 0) {
            effectiveIterationTicks = Math.max(1, Mth.ceil(cpAmount * 0.5f));
        }
        if (cpAmount > 0.0f) {
            occupations.add(new AbilityData.CpOccupationData(
                    cpAmount,
                    effectiveIterationTicks,
                    skill.getKeyString(),
                    isPermanent,
                    normalizedStackGroup
            ));
            cpData.setAvailableCP(cpData.getAvailableCP() - cpAmount, getMaxCP(uuid));
            enterOverloadIfDepleted(uuid, cpData);
        }
        if (mpAmount > 0.0f) {
            cpData.setCurrMP(Math.max(0.0f, cpData.getCurrMP() - mpAmount));
        }
        playerData.markDirty();
        return true;
    }

    static boolean canAffordCombinedCost(
            float availableCp,
            float currentMp,
            float cpCost,
            float mpCost
    ) {
        if (!Float.isFinite(availableCp) || !Float.isFinite(currentMp)
                || !Float.isFinite(cpCost) || !Float.isFinite(mpCost)
                || cpCost < 0.0f || mpCost < 0.0f) return false;
        return availableCp + CP_EPSILON >= cpCost && currentMp + CP_EPSILON >= mpCost;
    }

    static long countTimedStacks(List<AbilityData.CpOccupationData> occupations, String stackGroup) {
        if (occupations == null || stackGroup == null || stackGroup.isBlank()) return 0;
        return occupations.stream()
                .filter(occupation -> !occupation.isPermanent())
                .filter(occupation -> stackGroup.equals(occupation.getStackGroup()))
                .count();
    }

    public void releaseMaintenanceOccupation(UUID uuid, String skillId) {
        modify(uuid, cpData -> {
            var playerData = playerDataManager.getData(uuid);
            if (playerData == null) return;
            var occupations = playerData.getMutableCpOccupations();

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
            var occupations = playerData.getMutableCpOccupations();
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

        var matching = playerData.getMutableCpOccupations().stream()
                .filter(occupation -> occupation.isPermanent()
                        && skill.getKeyString().equals(occupation.getSkillId()))
                .toList();
        if (matching.size() == 1 && Float.compare(matching.getFirst().getAmount(), amount) == 0) {
            return true;
        }
        return replacePermanentOccupationsAndTryOccupation(
                uuid, Map.of(skill, amount), null, 0.0f, 0);
    }

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
        var occupations = playerData.getMutableCpOccupations();
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
        enterOverloadIfDepleted(uuid, cpData);
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

    public boolean replacePermanentOccupationAndTryTimedOccupations(
            UUID uuid,
            Skill skill,
            float permanentAmount,
            List<TimedOccupationCharge> timedCharges
    ) {
        var plan = createMixedOccupationPlan(uuid, skill, permanentAmount, timedCharges);
        if (plan == null) return false;

        var playerData = plan.playerData();
        var cpData = playerData.getCpData();
        var occupations = playerData.getMutableCpOccupations();
        occupations.removeIf(occupation -> occupation.isPermanent()
                && plan.skill().getKeyString().equals(occupation.getSkillId()));
        if (plan.permanentAmount() > 0.0f) {
            occupations.add(new AbilityData.CpOccupationData(
                    plan.permanentAmount(), 0, plan.skill().getKeyString(), true));
        }
        for (var charge : plan.timedCharges()) {
            if (charge.amount() <= 0.0f) continue;
            occupations.add(new AbilityData.CpOccupationData(
                    charge.amount(), charge.iterationTicks(), plan.skill().getKeyString(), false));
        }
        cpData.setAvailableCP(plan.availableAfterRelease() - plan.totalRequired(), getMaxCP(uuid));
        enterOverloadIfDepleted(uuid, cpData);
        playerData.markDirty();
        syncManager.schedulePlayerSync(uuid, SyncTypes.CP_DATA);
        return true;
    }

    /**
     * Atomically adds a group of timed charges without changing the skill's permanent charge.
     */
    public boolean tryTimedOccupations(
            UUID uuid,
            Skill skill,
            List<TimedOccupationCharge> timedCharges
    ) {
        var playerData = playerDataManager.getData(uuid);
        if (playerData == null || skill == null || timedCharges == null) return false;
        var permanentAmount = playerData.getMutableCpOccupations().stream()
                .filter(AbilityData.CpOccupationData::isPermanent)
                .filter(occupation -> skill.getKeyString().equals(occupation.getSkillId()))
                .mapToDouble(AbilityData.CpOccupationData::getAmount)
                .sum();
        if (!Double.isFinite(permanentAmount) || permanentAmount > Float.MAX_VALUE) return false;
        return replacePermanentOccupationAndTryTimedOccupations(
                uuid, skill, (float) permanentAmount, timedCharges);
    }

    public boolean canReplacePermanentOccupationAndTryTimedOccupations(
            UUID uuid,
            Skill skill,
            float permanentAmount,
            List<TimedOccupationCharge> timedCharges
    ) {
        return createMixedOccupationPlan(uuid, skill, permanentAmount, timedCharges) != null;
    }

    private MixedOccupationPlan createMixedOccupationPlan(
            UUID uuid,
            Skill skill,
            float permanentAmount,
            List<TimedOccupationCharge> timedCharges
    ) {
        var playerData = playerDataManager.getData(uuid);
        if (playerData == null || skill == null || timedCharges == null
                || !Float.isFinite(permanentAmount) || permanentAmount < 0.0f) return null;
        var cpData = playerData.getCpData();
        if (cpData.getStatus() == AbilityData.Status.OVERLOAD || enterOverloadIfDepleted(uuid, cpData)) return null;

        var normalizedCharges = new ArrayList<TimedOccupationCharge>(timedCharges.size());
        var timedTotal = 0.0f;
        for (var charge : timedCharges) {
            if (charge == null || !Float.isFinite(charge.amount()) || charge.amount() < 0.0f) return null;
            if (charge.amount() <= 0.0f) continue;
            var iterationTicks = charge.iterationTicks() > 0
                    ? charge.iterationTicks()
                    : Math.max(1, Mth.ceil(charge.amount() * 0.5f));
            normalizedCharges.add(new TimedOccupationCharge(charge.amount(), iterationTicks));
            timedTotal += charge.amount();
            if (!Float.isFinite(timedTotal)) return null;
        }

        var occupations = playerData.getMutableCpOccupations();
        var maxStacks = getMaxStacks(uuid, skill, getSkillLevel(playerData, skill));
        if (maxStacks != Skill.NO_STACK_LIMIT) {
            var currentStacks = occupations.stream()
                    .filter(occupation -> !occupation.isPermanent())
                    .filter(occupation -> skill.getKeyString().equals(occupation.getSkillId()))
                    .count();
            if (currentStacks + normalizedCharges.size() > maxStacks) return null;
        }

        var released = occupations.stream()
                .filter(AbilityData.CpOccupationData::isPermanent)
                .filter(occupation -> skill.getKeyString().equals(occupation.getSkillId()))
                .mapToDouble(AbilityData.CpOccupationData::getAmount)
                .sum();
        var totalRequired = permanentAmount + timedTotal;
        if (!Float.isFinite(totalRequired)
                || !isAtomicReplacementAffordable(cpData.getAvailableCP(), (float) released, totalRequired)) {
            return null;
        }
        return new MixedOccupationPlan(
                playerData,
                skill,
                permanentAmount,
                List.copyOf(normalizedCharges),
                cpData.getAvailableCP() + (float) released,
                totalRequired
        );
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
        if (enterOverloadIfDepleted(uuid, cpData)) return null;
        if (!Float.isFinite(timedAmount) || timedAmount < 0) return null;
        if (timedAmount > 0 && timedSkill == null) return null;

        var replacementIds = new HashSet<String>();
        var replacementTotal = 0.0f;
        for (var entry : permanentAmounts.entrySet()) {
            var skill = entry.getKey();
            var amount = entry.getValue();
            if (skill == null || amount == null || !Float.isFinite(amount) || amount < 0) return null;
            if (!replacementIds.add(skill.getKeyString())) return null;
            replacementTotal += amount;
            if (!Float.isFinite(replacementTotal)) return null;
        }

        var occupations = playerData.getMutableCpOccupations();
        if (timedAmount > 0) {
            var maxStacks = getMaxStacks(
                    uuid,
                    timedSkill,
                    getSkillLevel(playerData, timedSkill)
            );
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
                : Math.max(1, Mth.ceil(timedAmount * 0.5f));
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
        if (cpData.getStatus() == AbilityData.Status.OVERLOAD
                || enterOverloadIfDepleted(uuid, cpData)
                || cpData.getAvailableCP() < amount) return false;

        var skillData = playerData.getSkillDataMap().get(skill.getKeyString());
        var level = skillData == null ? 0 : skill.getLevelForProficiency(skillData.getProficiency());
        var maxStacks = getMaxStacks(uuid, skill, level);
        if (maxStacks != Skill.NO_STACK_LIMIT) {
            var currentStacks = playerData.getMutableCpOccupations().stream()
                    .filter(occupation -> !occupation.isPermanent())
                    .filter(occupation -> skill.getKeyString().equals(occupation.getSkillId()))
                    .count();
            if (currentStacks >= maxStacks) return false;
        }
        if (!action.getAsBoolean()) return false;
        return tryOccupation(uuid, amount, skill, iterationTicks, false);
    }

    int getMaxStacks(UUID uuid, Skill skill, int skillLevel) {
        var base = skill.getMaxStacks(skillLevel);
        return base == Skill.NO_STACK_LIMIT
                ? Skill.NO_STACK_LIMIT
                : Math.max(0, base + getBonuses(uuid).stackBonus());
    }

    @SubscribeEvent
    public void onPlayerEat(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer player && !player.level().isClientSide()) {
            var itemStack = event.getItem();
            if (itemStack.has(DataComponents.FOOD)) {
                var food = itemStack.get(DataComponents.FOOD);
                if (food != null) {
                    var saturationGained = food.nutrition() * food.saturation() * 2.0f;
                    var immediateRecovery = Math.max(0, (int) (saturationGained * 5));
                    var durationTicks = foodSpRecoveryDurationTicks(food.nutrition());
                    modify(player.getUUID(), data -> {
                        if (immediateRecovery > 0) data.addSP(immediateRecovery);
                        data.addFoodSpRecoveryTicks(durationTicks);
                    });
                }
            }
        }
    }

    @SubscribeEvent
    public void onAdvancementEarn(AdvancementEvent.AdvancementEarnEvent event) {
        var display = event.getAdvancement().value().display().orElse(null);
        if (display != null && display.getType() == AdvancementType.CHALLENGE) {
            var uuid = event.getEntity().getUUID();
            var playerData = playerDataManager.getData(uuid);
            if (playerData != null && playerData.addChallengeCpBonus(5.0f)) {
                refreshCommonSkillBonuses(uuid);
            }
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
        refreshCommonSkillBonuses(uuid);
    }

    public float getLevelBasicCP(int levelCode) {
        return BASE_MAX_CP;
    }

    public void setMaxCP(UUID uuid, float newMaxCP) {
        if (uuid == null) return;
        var safeMaxCP = normalizeDebugMaxCP(newMaxCP);
        debugMaxCpOverrides.put(uuid, safeMaxCP);
        modify(uuid, cpData -> cpData.setAvailableCP(cpData.getAvailableCP(), safeMaxCP));
        syncManager.schedulePlayerSync(uuid, SyncTypes.CP_DATA);
    }

    public void setBaseMaxCP(UUID uuid, float newBaseMaxCP) {
        refreshCommonSkillBonuses(uuid);
    }

    public float getAbilityExp(UUID uuid) {
        return query(uuid, AbilityData::getAbilityExp, 0f);
    }

    public void setAbilityExp(UUID uuid, float amount) {
        modify(uuid, cpData -> cpData.setAbilityExp(amount));
    }

    public float getAbilityExpMax(UUID uuid) {
        var playerData = playerDataManager.getData(uuid);
        if (playerData == null) return 1f;

        var categoryId = playerData.getAbilityCategory();

        var categoryRef = Registries.ABILITY_CATEGORIES.get(Identifier.parse(categoryId));
        if (categoryRef.isEmpty()) return 1f;

        return LearningHelper.getAbilityExpRequirement(categoryRef.get().value(), getLevel(uuid));
    }

    public boolean canLevelUp(UUID uuid) {
        var level = getLevel(uuid);
        if (level >= 5) return false;
        if (playerDataManager.getPlayerAbilityCategory(uuid) == AbilityCategories.LEVEL0.get()) return level == 0;
        return getAbilityExp(uuid) >= getAbilityExpMax(uuid);
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
            cpData.setAbilityExp(Math.min(getAbilityExpMax(uuid), cpData.getAbilityExp() + amount * mul));
        });
    }

    public float getMaxCP(UUID uuid) {
        var playerData = playerDataManager.getData(uuid);
        var naturalMaxCP = BASE_MAX_CP;
        if (playerData != null) {
            naturalMaxCP = playerData.isMaxCpInitialized()
                    ? normalizeDebugMaxCP(playerData.getCpData().getMaxCP())
                    : initialPersistentMaxCp(
                    playerData.getCpData().getMaxCP(),
                    Math.max(
                            playerData.getAppliedCommonSkillMaxCpBonus(),
                            getDerivedMaxCpBonus(uuid)
                    )
            );
        }
        return resolveEffectiveMaxCP(naturalMaxCP, debugMaxCpOverrides.get(uuid));
    }

    public float getAvailableCP(UUID uuid) {
        return query(uuid, AbilityData::getAvailableCP, 0f);
    }

    public void setAvailableCP(UUID uuid, float availableCP) {
        var safeCP = Float.isFinite(availableCP) ? availableCP : 0f;
        modify(uuid, cpData -> {
            if (Float.compare(cpData.getAvailableCP(), safeCP) != 0) {
                cpData.setAvailableCP(safeCP, getMaxCP(uuid));
                enterOverloadIfDepleted(uuid, cpData);
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
        var caller = STATE_STACK_WALKER.walk(frames -> frames
                .dropWhile(frame -> frame.getDeclaringClass() != PlayerCPManager.class
                        || !frame.getMethodName().equals("setStatus"))
                .skip(1)
                .map(StackWalker.StackFrame::getDeclaringClass)
                .findFirst()
                .orElse(null));
        var callerDomain = caller == null ? null : caller.getProtectionDomain();
        var academyDomain = AcademyCraft.class.getProtectionDomain();
        var allowed = callerDomain != null && callerDomain == academyDomain;
        if (!allowed && callerDomain != null && callerDomain.getCodeSource() != null) {
            var callerLocation = callerDomain.getCodeSource().getLocation();
            var academyLocation = academyDomain == null || academyDomain.getCodeSource() == null
                    ? null : academyDomain.getCodeSource().getLocation();
            allowed = callerLocation != null && callerLocation.equals(academyLocation);
        }
        if (!allowed) return;

        modify(uuid, cpData -> cpData.setStatus(status));
    }

    public int getStateTimer(UUID uuid) {
        return query(uuid, AbilityData::getStateTimer, 0);
    }

    public void setStateTimer(UUID uuid, int stateTimer) {
        var caller = STATE_STACK_WALKER.walk(frames -> frames
                .dropWhile(frame -> frame.getDeclaringClass() != PlayerCPManager.class
                        || !frame.getMethodName().equals("setStateTimer"))
                .skip(1)
                .map(StackWalker.StackFrame::getDeclaringClass)
                .findFirst()
                .orElse(null));
        var callerDomain = caller == null ? null : caller.getProtectionDomain();
        var academyDomain = AcademyCraft.class.getProtectionDomain();
        var allowed = callerDomain != null && callerDomain == academyDomain;
        if (!allowed && callerDomain != null && callerDomain.getCodeSource() != null) {
            var callerLocation = callerDomain.getCodeSource().getLocation();
            var academyLocation = academyDomain == null || academyDomain.getCodeSource() == null
                    ? null : academyDomain.getCodeSource().getLocation();
            allowed = callerLocation != null && callerLocation.equals(academyLocation);
        }
        if (!allowed) return;

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
        var ratio = Mth.clamp(getFreeCPRatio(uuid), 0, 1);
        var cpMultiplier = ratio >= 0.5f
                ? 1.0f
                : 0.25f + (ratio / 0.5f) * 0.75f;
        return cpMultiplier * getAbilityPowerMultiplier(uuid);
    }

    public float getCalculationEfficiency(UUID uuid) {
        return calculationEfficiency(getMaxCP(uuid));
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

        var desiredBonus = getDerivedMaxCpBonus(uuid);
        var appliedBonus = playerData.getAppliedCommonSkillMaxCpBonus();
        var cpData = playerData.getCpData();
        var debugMaxCP = debugMaxCpOverrides.get(uuid);
        var changed = false;

        if (!playerData.isMaxCpInitialized()) {
            // Older saves stored a 100-point base and rebuilt all growth on login. Promote the
            // already-earned derived total into the persisted max without double-applying it.
            var trackedBonus = Math.max(appliedBonus, desiredBonus);
            cpData.setMaxCP(initialPersistentMaxCp(cpData.getMaxCP(), trackedBonus));
            playerData.setAppliedCommonSkillMaxCpBonus(trackedBonus);
            playerData.setMaxCpInitialized(true);
            appliedBonus = trackedBonus;
            changed = true;
        }

        if (desiredBonus > appliedBonus) {
            var oldMaxCp = cpData.getMaxCP();
            var newMaxCp = applyDerivedMaxCpGrowth(oldMaxCp, appliedBonus, desiredBonus);
            var delta = newMaxCp - oldMaxCp;
            cpData.setMaxCP(newMaxCp);
            var effectiveMaxCp = resolveEffectiveMaxCP(newMaxCp, debugMaxCP);
            cpData.setAvailableCP(cpData.getAvailableCP() + delta, effectiveMaxCp);
            playerData.setAppliedCommonSkillMaxCpBonus(desiredBonus);
            changed = true;
        }

        var effectiveMaxCp = resolveEffectiveMaxCP(cpData.getMaxCP(), debugMaxCP);
        if (cpData.getAvailableCP() > effectiveMaxCp) {
            cpData.setAvailableCP(effectiveMaxCp, effectiveMaxCp);
            changed = true;
        }
        if (changed) syncManager.schedulePlayerSync(uuid, SyncTypes.CP_DATA);
    }

    private float getDerivedMaxCpBonus(UUID uuid) {
        var playerData = playerDataManager.getData(uuid);
        if (playerData == null) return 0.0f;
        var category = playerDataManager.getPlayerAbilityCategory(uuid);

        var skillBonus = 0.0f;
        for (var entry : playerData.getSkillDataMap().entrySet()) {
            var id = Identifier.tryParse(entry.getKey());
            if (id == null) continue;
            var skill = Registries.SKILLS.get(id).map(reference -> reference.value()).orElse(null);
            if (skill == null || !LearningHelper.isSkillAvailableForCategory(category, skill)) continue;
            skillBonus += CommonSkillBonuses.reachedProficiencyThresholds(
                    entry.getValue().getProficiency()
            ) * 10.0f;
        }
        skillBonus = Math.min(skillBonus, MAX_SKILL_PROFICIENCY_CP_BONUS);

        return skillBonus
                + Math.min(playerData.getChallengeCpBonus(), MAX_CHALLENGE_CP_BONUS)
                + abilityLevelCpBonus(getLevel(uuid))
                + getBonuses(uuid).maxCp();
    }

    private CommonSkillBonuses.Bonuses getBonuses(UUID uuid) {
        var playerData = playerDataManager.getData(uuid);
        if (playerData == null) return CommonSkillBonuses.NONE;

        var category = playerDataManager.getPlayerAbilityCategory(uuid);
        return CommonSkillBonuses.calculate(
                playerData.getSkillDataMap(),
                getLevel(uuid),
                category.supportsCommonSkills()
        );
    }

    public float getRangeMultiplier(UUID uuid) {
        var ratio = Mth.clamp(getFreeCPRatio(uuid), 0, 1);
        if (ratio >= 0.5f) return 1.0f;
        return 0.50f + (ratio / 0.5f) * 0.50f;
    }

    public float getEffectiveDistanceMultiplier(UUID uuid) {
        var ratio = Mth.clamp(getFreeCPRatio(uuid), 0, 1);
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

    record CpRecoveryPlan(float recoveredCp, float remainderCp, int spCost) {
    }

    private record AtomicOccupationPlan(
            Player playerData,
            Map<Skill, Float> permanentAmounts,
            Set<String> replacementIds,
            Skill timedSkill,
            float timedAmount,
            int iterationTicks,
            float availableAfterRelease,
            float totalRequired
    ) {
    }

    public record TimedOccupationCharge(float amount, int iterationTicks) {
    }

    private record MixedOccupationPlan(
            Player playerData,
            Skill skill,
            float permanentAmount,
            List<TimedOccupationCharge> timedCharges,
            float availableAfterRelease,
            float totalRequired
    ) {
    }
}
