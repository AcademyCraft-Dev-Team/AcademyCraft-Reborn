package org.academy.internal.server.ability;

import net.minecraft.server.level.ServerPlayer;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.AbilityResourceSpec;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.ability.SyncTypes;
import org.academy.api.common.ability.darkmatter.DarkmatterPhaseSnapshot;
import org.academy.api.common.ability.darkmatter.DarkmatterResourceService;
import org.academy.api.common.ability.darkmatter.DarkmatterResourceView;
import org.academy.api.common.data.AbilityData;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.darkmatter.Darkmatter;
import org.academy.internal.common.ability.darkmatter.SyncDarkmatterStatePacket;
import org.academy.internal.common.ability.darkmatter.skills.lv5.DarkmatterSixWings;
import org.academy.internal.server.world.level.storage.Player;
import org.misaka.MisakaNetworkServer;

import java.util.Map;

/**
 * Owns the natural/generated MP split, generated-MP CP debt and phase allocation.
 */
public final class DarkmatterResourceManager implements AbilitySubsystem, DarkmatterResourceService {
    private static final float EPSILON = 1.0e-4f;
    private static final float BASE_MATTER = 100.0f;
    private static final float MATTER_PER_LEVEL_SQUARED = 8.0f;
    private static final float NATURAL_RECOVERY_PER_SECOND = 1.0f;
    /**
     * The generation formula is clamped to at least one CP for every created MP.
     */
    private static final float MIN_CREATED_CP_PER_UNIT = 1.0f;
    private static final String GENERATION_SKILL_ID = "academy:darkmatter_generation";
    private static final AbilityResourceSpec RESOURCE = Darkmatter.MATTER_RESOURCE;

    private final PlayerDataManager playerDataManager;
    private final PlayerCPManager playerCPManager;
    private final SyncManager syncManager;

    public DarkmatterResourceManager(PlayerDataManager playerDataManager,
                                     PlayerCPManager playerCPManager,
                                     SyncManager syncManager) {
        this.playerDataManager = playerDataManager;
        this.playerCPManager = playerCPManager;
        this.syncManager = syncManager;
    }

    @Override
    public void onPlayerLogin(ServerPlayer player) {
        reconcile(player);
        scheduleAllSync(player);
    }

    @Override
    public void tick(ServerPlayer player) {
        if (player.tickCount % 20 != 0) return;
        reconcile(player);
        recoverNaturalMatter(player);
    }

    @Override
    public void processSync(ServerPlayer player) {
        var data = playerDataManager.getData(player.getUUID());
        if (data == null) return;
        var snapshot = getPhaseSnapshot(player);
        var state = data.getDarkmatterState();
        MisakaNetworkServer.send(player, new SyncDarkmatterStatePacket(
                snapshot.abilityLevel(), snapshot.totalPoints(), snapshot.alphaPoints(),
                snapshot.gammaActive(), state.getNaturalMatter(), state.getCreatedMatter(),
                state.getReservedMatter()));
    }

    public DarkmatterPhaseSnapshot getPhaseSnapshot(ServerPlayer player) {
        var level = playerCPManager.getLevel(player.getUUID());
        var data = playerDataManager.getData(player.getUUID());
        return data == null
                ? DarkmatterPhaseSnapshot.of(level, level * 25, false)
                : data.getDarkmatterState().phaseSnapshot(
                level, DarkmatterSixWings.Server.isActive(player));
    }

    public DarkmatterResourceView getView(ServerPlayer player) {
        var data = playerDataManager.getData(player.getUUID());
        var base = getBaseCapacity(player);
        if (data == null) return new DarkmatterResourceView(0, 0, 0, 0, base, base);
        if (ensureInitialized(data, player)) commit(data, player);
        var state = data.getDarkmatterState();
        var effective = Math.max(0.0f, base - state.getReservedMatter());
        return new DarkmatterResourceView(
                state.getNaturalMatter(), state.getCreatedMatter(), state.getCreatedCpDebt(),
                state.getReservedMatter(), base, effective);
    }

    public float getPhase(ServerPlayer player) {
        var snapshot = getPhaseSnapshot(player);
        return snapshot.totalPoints() <= 0
                ? 0.0f : snapshot.betaRatio() * 2.0f - 1.0f;
    }

    public float getAlpha(ServerPlayer player) {
        return getPhaseSnapshot(player).alphaPower();
    }

    public float getBeta(ServerPlayer player) {
        return getPhaseSnapshot(player).betaPower();
    }

    public float getGamma(ServerPlayer player) {
        return getPhaseSnapshot(player).activeGammaPower();
    }

    public float getBaseCapacity(ServerPlayer player) {
        return baseCapacity(playerCPManager.getLevel(player.getUUID()));
    }

    public float getEffectiveCapacity(ServerPlayer player) {
        return getView(player).effectiveCapacity();
    }

    public boolean setAlphaPoints(ServerPlayer player, int points) {
        if (!supportsMatter(player)) return false;
        var data = playerDataManager.getData(player.getUUID());
        if (data == null || !data.getDarkmatterState().setAlphaPoints(
                playerCPManager.getLevel(player.getUUID()), points)) return false;
        data.markDirty();
        syncManager.schedulePlayerSync(player.getUUID(), SyncTypes.DARKMATTER_STATE);
        return true;
    }

    public boolean tuneAlphaPoints(ServerPlayer player, float deltaPoints) {
        if (!Float.isFinite(deltaPoints) || deltaPoints == 0.0f || !supportsMatter(player)) return false;
        var data = playerDataManager.getData(player.getUUID());
        if (data == null || !data.getDarkmatterState().tuneAlphaPoints(
                playerCPManager.getLevel(player.getUUID()), deltaPoints)) return false;
        data.markDirty();
        syncManager.schedulePlayerSync(player.getUUID(), SyncTypes.DARKMATTER_STATE);
        return true;
    }

    /**
     * Legacy normalized tuning entry retained for integrations compiled against the old API.
     */
    public boolean tunePhase(ServerPlayer player, float delta) {
        var total = getPhaseSnapshot(player).totalPoints();
        return tuneAlphaPoints(player, -delta * total * 0.5f);
    }

    public boolean create(ServerPlayer player, float requestedUnits) {
        return create(player, requestedUnits, RESOURCE.occupiedCpPerUnit());
    }

    public boolean create(ServerPlayer player, float requestedUnits, float cpPerUnit) {
        var units = normalizeUnits(requestedUnits);
        var unitCost = Math.max(MIN_CREATED_CP_PER_UNIT,
                normalizePositive(cpPerUnit, RESOURCE.occupiedCpPerUnit()));
        if (units <= 0.0f || !supportsMatter(player)) return false;
        reconcile(player);
        var data = playerDataManager.getData(player.getUUID());
        if (data == null) return false;
        var state = data.getDarkmatterState();
        var creatable = Math.min(units,
                Math.max(0.0f, data.getCpData().getAvailableCP() - EPSILON) / unitCost);
        if (creatable <= EPSILON) return false;
        var nextDebt = state.getCreatedCpDebt() + creatable * unitCost;
        if (!replaceMatterOccupation(player, nextDebt, null, 0.0f, 0)) return false;
        state.setCreatedMatter(state.getCreatedMatter() + creatable);
        state.setCreatedCpDebt(nextDebt);
        commit(data, player);
        return true;
    }

    public boolean erase(ServerPlayer player, float requestedUnits, int iterationTicks) {
        return consume(player, requestedUnits, Skills.DARKMATTER_GENERATION.get(), iterationTicks);
    }

    public boolean consume(ServerPlayer player, float requestedUnits, Skill consumer, int iterationTicks) {
        var units = normalizeUnits(requestedUnits);
        if (units <= 0.0f || consumer == null || !supportsMatter(player)) return false;
        reconcile(player);
        var data = playerDataManager.getData(player.getUUID());
        if (data == null) return false;
        var state = data.getDarkmatterState();
        var plan = planConsumption(state.getNaturalMatter(), state.getCreatedMatter(),
                state.getCreatedCpDebt(), units);
        if (plan == null) return false;

        if (!replaceMatterOccupation(player, plan.nextCreatedCpDebt(), consumer,
                plan.releasedCpDebt(),
                Math.max(1, iterationTicks))) return false;
        state.setCreatedMatter(plan.nextCreatedMatter());
        state.setCreatedCpDebt(plan.nextCreatedCpDebt());
        state.setNaturalMatter(plan.nextNaturalMatter());
        commit(data, player);
        return true;
    }

    public float consumeUpTo(ServerPlayer player, float requestedUnits,
                             Skill consumer, int iterationTicks) {
        var data = playerDataManager.getData(player.getUUID());
        if (data == null) return 0.0f;
        reconcile(player);
        var actual = Math.min(normalizeUnits(requestedUnits), data.getDarkmatterState().totalMatter());
        return actual > EPSILON && consume(player, actual, consumer, iterationTicks) ? actual : 0.0f;
    }

    @Override
    public boolean creditEarnedMatter(ServerPlayer player, float units) {
        var amount = normalizeUnits(units);
        if (amount <= 0.0f || !supportsMatter(player)) return false;
        reconcile(player);
        var data = playerDataManager.getData(player.getUUID());
        if (data == null) return false;
        var state = data.getDarkmatterState();
        var next = state.getNaturalMatter() + amount;
        if (!Float.isFinite(next)) next = Float.MAX_VALUE;
        state.setNaturalMatter(next);
        commit(data, player);
        return true;
    }

    public boolean reserve(ServerPlayer player, float units, Skill consumer, int iterationTicks) {
        var amount = normalizeUnits(units);
        if (amount <= 0.0f || !supportsMatter(player)) return false;
        reconcile(player);
        var data = playerDataManager.getData(player.getUUID());
        if (data == null) return false;
        var state = data.getDarkmatterState();
        if (state.getReservedMatter() + amount > getBaseCapacity(player) + EPSILON) return false;
        if (!consume(player, amount, consumer, iterationTicks)) return false;
        state.setReservedMatter(state.getReservedMatter() + amount);
        commit(data, player);
        return true;
    }

    public float releaseReservation(ServerPlayer player, float units) {
        var data = playerDataManager.getData(player.getUUID());
        if (data == null) return 0.0f;
        var state = data.getDarkmatterState();
        var released = Math.min(normalizeUnits(units), state.getReservedMatter());
        if (released <= EPSILON) return 0.0f;
        state.setReservedMatter(state.getReservedMatter() - released);
        commit(data, player);
        return released;
    }

    public void requestSync(ServerPlayer player) {
        if (player != null) scheduleAllSync(player);
    }

    /**
     * Controlled mutation used only by the operator debug command and automated game tests.
     */
    public boolean debugSetPools(ServerPlayer player, float natural, float created,
                                 float cpDebt, float reserved) {
        if (!supportsMatter(player) || !Float.isFinite(natural) || !Float.isFinite(created)
                || !Float.isFinite(cpDebt) || !Float.isFinite(reserved)
                || natural < 0.0f || created < 0.0f || cpDebt < 0.0f || reserved < 0.0f
                || cpDebt + EPSILON < created * MIN_CREATED_CP_PER_UNIT
                || created <= EPSILON && cpDebt > EPSILON
                || reserved > getBaseCapacity(player) + EPSILON) return false;
        var data = playerDataManager.getData(player.getUUID());
        if (data == null || !replaceMatterOccupation(player, cpDebt, null, 0.0f, 0)) return false;
        var state = data.getDarkmatterState();
        state.initializeResource(natural, created, cpDebt);
        state.setReservedMatter(reserved);
        commit(data, player);
        return true;
    }

    public void reconcile(ServerPlayer player) {
        var data = playerDataManager.getData(player.getUUID());
        if (data == null) return;
        var state = data.getDarkmatterState();
        var level = playerCPManager.getLevel(player.getUUID());
        var changed = state.reconcilePhase(level);
        changed |= state.repair();

        if (!supportsMatter(player)) {
            var hadMatter = state.totalMatter() > EPSILON || state.getReservedMatter() > EPSILON
                    || data.getCpData().getCurrMP() > EPSILON || data.getCpData().getMaxMP() > EPSILON;
            state.initializeResource(0.0f, 0.0f, 0.0f);
            state.setReservedMatter(0.0f);
            data.getCpData().setCurrMP(0.0f);
            data.getCpData().setMaxMP(0.0f);
            removeMatterOccupation(player, data);
            if (changed || hadMatter) commit(data, player);
            return;
        }

        changed |= ensureInitialized(data, player);
        var minimumDebt = state.getCreatedMatter() * MIN_CREATED_CP_PER_UNIT;
        if (state.getCreatedCpDebt() + EPSILON < minimumDebt) {
            state.setCreatedCpDebt(minimumDebt);
            changed = true;
        }
        var desiredDebt = state.getCreatedCpDebt();
        var existingDebt = matterOccupation(data);
        if (Math.abs(existingDebt - desiredDebt) > EPSILON) {
            if (!replaceMatterOccupation(player, desiredDebt, null, 0.0f, 0)) {
                var affordableDebt = Math.min(desiredDebt,
                        Math.max(0.0f, data.getCpData().getAvailableCP()) + existingDebt);
                var ratio = desiredDebt <= EPSILON ? 0.0f : affordableDebt / desiredDebt;
                state.setCreatedMatter(state.getCreatedMatter() * ratio);
                state.setCreatedCpDebt(affordableDebt);
                replaceMatterOccupation(player, affordableDebt, null, 0.0f, 0);
            }
            changed = true;
        }
        changed |= mirrorToAbilityData(data, player);
        if (changed) commit(data, player);
    }

    private void recoverNaturalMatter(ServerPlayer player) {
        if (!supportsMatter(player)) return;
        var data = playerDataManager.getData(player.getUUID());
        if (data == null) return;
        var state = data.getDarkmatterState();
        var effective = Math.max(0.0f, getBaseCapacity(player) - state.getReservedMatter());
        var recovered = recoverNaturalMatter(state.getNaturalMatter(), effective);
        if (recovered <= state.getNaturalMatter() + EPSILON) return;
        state.setNaturalMatter(recovered);
        commit(data, player);
    }

    private boolean ensureInitialized(Player data, ServerPlayer player) {
        var state = data.getDarkmatterState();
        if (state.isResourceInitialized()) return false;
        var current = data.getCpData().getCurrMP();
        var debt = matterOccupation(data);
        var created = Math.min(current, unitsForOccupation(debt, RESOURCE));
        state.initializeResource(Math.max(0.0f, current - created), created, debt);
        if (current > EPSILON || debt > EPSILON) {
            AcademyCraft.LOGGER.info(
                    "Migrated legacy darkmatter MP ledger for {}: natural={}, created={} from permanent CP debt={}",
                    player.getGameProfile().name(), Math.max(0.0f, current - created), created, debt);
        }
        return true;
    }

    private boolean mirrorToAbilityData(Player data, ServerPlayer player) {
        var cpData = data.getCpData();
        var state = data.getDarkmatterState();
        var effective = Math.max(0.0f, getBaseCapacity(player) - state.getReservedMatter());
        var changed = false;
        if (Math.abs(cpData.getCurrMP() - state.totalMatter()) > EPSILON) {
            cpData.setCurrMP(state.totalMatter());
            changed = true;
        }
        if (Math.abs(cpData.getMaxMP() - effective) > EPSILON) {
            cpData.setMaxMP(effective);
            changed = true;
        }
        return changed;
    }

    private void commit(Player data, ServerPlayer player) {
        mirrorToAbilityData(data, player);
        data.markDirty();
        scheduleAllSync(player);
    }

    private void scheduleAllSync(ServerPlayer player) {
        syncManager.schedulePlayerSync(player.getUUID(), SyncTypes.CP_DATA);
        syncManager.schedulePlayerSync(player.getUUID(), SyncTypes.DARKMATTER_STATE);
    }

    private boolean supportsMatter(ServerPlayer player) {
        var data = playerDataManager.getData(player.getUUID());
        return data != null
                && playerDataManager.getPlayerAbilityCategory(player.getUUID()) == AbilityCategories.DARKMATTER.get()
                && data.isSkillLearned(GENERATION_SKILL_ID);
    }

    private boolean replaceMatterOccupation(ServerPlayer player, float permanent,
                                            Skill timedSkill, float timed, int iterationTicks) {
        return playerCPManager.replacePermanentOccupationsAndTryOccupation(
                player.getUUID(), Map.of(Skills.DARKMATTER_GENERATION.get(), Math.max(0.0f, permanent)),
                timedSkill, Math.max(0.0f, timed), Math.max(0, iterationTicks));
    }

    private void removeMatterOccupation(ServerPlayer player, Player data) {
        if (matterOccupation(data) <= EPSILON) return;
        replaceMatterOccupation(player, 0.0f, null, 0.0f, 0);
    }

    private static float matterOccupation(Player data) {
        return (float) data.getCpOccupations().stream()
                .filter(AbilityData.CpOccupationData::isPermanent)
                .filter(occupation -> GENERATION_SKILL_ID.equals(occupation.getSkillId()))
                .mapToDouble(AbilityData.CpOccupationData::getAmount)
                .sum();
    }

    static float baseCapacity(int abilityLevel) {
        var level = Math.clamp(abilityLevel, 0, 5);
        return BASE_MATTER + level * level * MATTER_PER_LEVEL_SQUARED;
    }

    static float creatableUnits(float availableCp, float requestedUnits, AbilityResourceSpec resource) {
        if (!Float.isFinite(availableCp) || !Float.isFinite(requestedUnits)
                || requestedUnits <= 0.0f || resource == null) return 0.0f;
        var usableCp = Math.max(0.0f, availableCp - EPSILON);
        return Math.min(requestedUnits, usableCp / resource.occupiedCpPerUnit());
    }

    static float recoverNaturalTotal(float current, float created, float baseCapacity) {
        if (!Float.isFinite(current) || !Float.isFinite(created) || !Float.isFinite(baseCapacity)) return 0.0f;
        var safeCurrent = Math.max(0.0f, current);
        var safeCreated = Math.min(safeCurrent, Math.max(0.0f, created));
        var natural = safeCurrent - safeCreated;
        return safeCurrent + Math.min(NATURAL_RECOVERY_PER_SECOND,
                Math.max(0.0f, Math.max(0.0f, baseCapacity) - natural));
    }

    static float recoverNaturalMatter(float naturalMatter, float effectiveCapacity) {
        if (!Float.isFinite(naturalMatter) || !Float.isFinite(effectiveCapacity)) return 0.0f;
        var natural = Math.max(0.0f, naturalMatter);
        var capacity = Math.max(0.0f, effectiveCapacity);
        if (natural >= capacity) return natural;
        return Math.min(capacity, natural + NATURAL_RECOVERY_PER_SECOND);
    }

    static ConsumptionPlan planConsumption(float naturalMatter, float createdMatter,
                                           float createdCpDebt, float requestedUnits) {
        if (!Float.isFinite(naturalMatter) || !Float.isFinite(createdMatter)
                || !Float.isFinite(createdCpDebt) || !Float.isFinite(requestedUnits)
                || naturalMatter < 0.0f || createdMatter < 0.0f
                || createdCpDebt < 0.0f || requestedUnits <= 0.0f
                || naturalMatter + createdMatter + EPSILON < requestedUnits) return null;
        var consumedCreated = Math.min(requestedUnits, createdMatter);
        var consumedNatural = Math.min(naturalMatter, requestedUnits - consumedCreated);
        var releasedDebt = createdMatter <= EPSILON
                ? 0.0f : createdCpDebt * (consumedCreated / createdMatter);
        return new ConsumptionPlan(
                Math.max(0.0f, naturalMatter - consumedNatural),
                Math.max(0.0f, createdMatter - consumedCreated),
                Math.max(0.0f, createdCpDebt - releasedDebt),
                Math.max(0.0f, releasedDebt),
                consumedCreated,
                consumedNatural
        );
    }

    static float affordableUnits(float availableCp, float releasedCp, float currentUnits,
                                 AbilityResourceSpec resource) {
        if (!Float.isFinite(availableCp) || !Float.isFinite(releasedCp)
                || !Float.isFinite(currentUnits) || resource == null) return 0.0f;
        var budget = Math.max(0.0f, availableCp) + Math.max(0.0f, releasedCp);
        return Math.min(Math.max(0.0f, currentUnits),
                Math.max(0.0f, budget / resource.occupiedCpPerUnit()));
    }

    private static float unitsForOccupation(float occupiedCp, AbilityResourceSpec resource) {
        if (!Float.isFinite(occupiedCp) || occupiedCp <= 0.0f || resource == null) return 0.0f;
        return occupiedCp / resource.occupiedCpPerUnit();
    }

    private static float normalizeUnits(float value) {
        return Float.isFinite(value) ? Math.max(0.0f, value) : 0.0f;
    }

    private static float normalizePositive(float value, float fallback) {
        return Float.isFinite(value) && value > 0.0f ? value : fallback;
    }

    record ConsumptionPlan(
            float nextNaturalMatter,
            float nextCreatedMatter,
            float nextCreatedCpDebt,
            float releasedCpDebt,
            float consumedCreatedMatter,
            float consumedNaturalMatter
    ) {
        float totalConsumedMatter() {
            return consumedCreatedMatter + consumedNaturalMatter;
        }
    }
}
