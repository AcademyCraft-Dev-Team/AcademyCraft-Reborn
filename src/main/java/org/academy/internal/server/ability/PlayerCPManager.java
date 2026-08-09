package org.academy.internal.server.ability;

import net.minecraft.advancements.AdvancementType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.LearningHelper;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.ability.SyncTypes;
import org.academy.api.common.ability.event.AbilityOverloadEvent;
import org.academy.api.common.ability.event.AbilityRecoveryEvent;
import org.academy.api.common.ability.pakcet.SyncAbilityDataPacket;
import org.academy.api.common.data.AbilityData;
import org.academy.api.common.registries.Registries;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.server.config.AbilityConfig;
import org.academy.internal.common.attribute.PlayerAttributeRuntime;
import org.misaka.MisakaNetworkServer;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.BooleanSupplier;

public class PlayerCPManager implements AbilitySubsystem {
    static final float BASE_MAX_CP = 100.0f;
    static final float MAX_SKILL_PROFICIENCY_CP_BONUS = 300.0f;
    static final float MAX_CHALLENGE_CP_BONUS = 200.0f;
    static final int OVERLOAD_TICKS = 200;
    private static final float TICKS_PER_ITERATION_POINT = 20.0f;
    private static final int FOOD_SP_RECOVERY_TICKS_PER_NUTRITION = 20 * 10;
    static final float RECOVERED_CP_PER_SP = 10.0f;
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
                player.getCpOccupations()
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

    private static void enterOverload(AbilityData cpData, ServerPlayer player) {
        cpData.setStatus(AbilityData.Status.OVERLOAD);
        cpData.setStateTimer(OVERLOAD_TICKS);
        NeoForge.EVENT_BUS.post(new AbilityOverloadEvent(player));
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
            recoverySteps = (int) Math.floor(progress / TICKS_PER_ITERATION_POINT);
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

            // 永久占用，跳过迭代
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

    /** Advances every timed stack independently so one cast never waits for earlier stacks. */
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
                (int) Math.floor((accumulated + CP_EPSILON) / safeRecoveredCpPerSp));
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

    record CpRecoveryPlan(float recoveredCp, float remainderCp, int spCost) {
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
        var level = (skillData != null) ? skill.getLevelForProficiency(skillData.getProficiency()) : 0;

        if (cpData.getStatus() == AbilityData.Status.OVERLOAD) return false;
        if (enterOverloadIfDepleted(uuid, cpData)) return false;
        if (cpData.getAvailableCP() < amount) return false;

        var maxStacks = getMaxStacks(uuid, skill, level);
        if (!isPermanent && maxStacks != Skill.NO_STACK_LIMIT) {
            var currentStacks = occupations.stream()
                    .filter(occupation -> !occupation.isPermanent())
                    .filter(occ -> skill.getKeyString().equals(occ.getSkillId()))
                    .count();
            if (currentStacks >= maxStacks) return false;
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
        enterOverloadIfDepleted(uuid, cpData);
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
        if (cpData.getStatus() == AbilityData.Status.OVERLOAD
                || enterOverloadIfDepleted(uuid, cpData)
                || cpData.getAvailableCP() < amount) return false;

        var skillData = playerData.getSkillDataMap().get(skill.getKeyString());
        var level = skillData == null ? 0 : skill.getLevelForProficiency(skillData.getProficiency());
        var maxStacks = getMaxStacks(uuid, skill, level);
        if (maxStacks != Skill.NO_STACK_LIMIT) {
            var currentStacks = playerData.getCpOccupations().stream()
                    .filter(occupation -> !occupation.isPermanent())
                    .filter(occupation -> skill.getKeyString().equals(occupation.getSkillId()))
                    .count();
            if (currentStacks >= maxStacks) return false;
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
        return data == null ? 0 : skill.getLevelForProficiency(data.getProficiency());
    }

    int getMaxStacks(UUID uuid, Skill skill, int skillLevel) {
        var base = skill.getMaxStacks(skillLevel);
        return base == Skill.NO_STACK_LIMIT
                ? Skill.NO_STACK_LIMIT
                : Math.max(0, base + getBonuses(uuid).stackBonus());
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

    static int foodSpRecoveryDurationTicks(int nutrition) {
        return (int) Math.min(
                Integer.MAX_VALUE,
                (long) Math.max(0, nutrition) * FOOD_SP_RECOVERY_TICKS_PER_NUTRITION
        );
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
        var naturalMaxCP = playerDataManager.getData(uuid) == null
                ? BASE_MAX_CP
                : BASE_MAX_CP + getDerivedMaxCpBonus(uuid);
        return resolveEffectiveMaxCP(naturalMaxCP, debugMaxCpOverrides.get(uuid));
    }

    static float normalizeDebugMaxCP(float maxCP) {
        return Float.isFinite(maxCP) ? Math.max(0.0f, maxCP) : 0.0f;
    }

    static float resolveEffectiveMaxCP(float naturalMaxCP, Float debugOverride) {
        return debugOverride == null
                ? normalizeDebugMaxCP(naturalMaxCP)
                : normalizeDebugMaxCP(debugOverride);
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
        return calculationEfficiency(getMaxCP(uuid));
    }

    static float calculationEfficiency(float maxCp) {
        return Float.isFinite(maxCp) ? Math.max(0.0f, maxCp) * 0.0005f : 0.0f;
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
        if (debugMaxCP != null) {
            var changed = false;
            if (Float.compare(cpData.getMaxCP(), BASE_MAX_CP) != 0) {
                cpData.setMaxCP(BASE_MAX_CP);
                changed = true;
            }
            if (Float.compare(desiredBonus, appliedBonus) != 0) {
                playerData.setAppliedCommonSkillMaxCpBonus(desiredBonus);
                changed = true;
            }
            if (cpData.getAvailableCP() > debugMaxCP) {
                cpData.setAvailableCP(debugMaxCP, debugMaxCP);
                changed = true;
            }
            if (changed) syncManager.schedulePlayerSync(uuid, SyncTypes.CP_DATA);
            return;
        }
        if (Float.compare(desiredBonus, appliedBonus) == 0
                && Float.compare(cpData.getMaxCP(), BASE_MAX_CP) == 0) return;

        var oldEffectiveMaxCP = cpData.getMaxCP() + appliedBonus;
        var effectiveMaxCP = BASE_MAX_CP + desiredBonus;
        var diff = effectiveMaxCP - oldEffectiveMaxCP;
        cpData.setMaxCP(BASE_MAX_CP);
        cpData.setAvailableCP(cpData.getAvailableCP() + diff, effectiveMaxCP);
        playerData.setAppliedCommonSkillMaxCpBonus(desiredBonus);
        syncManager.schedulePlayerSync(uuid, SyncTypes.CP_DATA);
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

    static float abilityLevelCpBonus(int level) {
        if (level >= 5) return 300.0f;
        if (level == 4) return 140.0f;
        if (level == 3) return 60.0f;
        if (level == 2) return 20.0f;
        return 0.0f;
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
