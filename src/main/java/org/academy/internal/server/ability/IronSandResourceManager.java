package org.academy.internal.server.ability;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.ability.SkillProficiencyProfile;
import org.academy.api.common.ability.SyncTypes;
import org.academy.api.common.ability.electromaster.IronSandTuning;
import org.academy.api.server.ability.AbilityResourceAccount;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.SkillAvailability;
import org.academy.api.server.ability.electromaster.IronSandResourceService;
import org.academy.api.server.damage.HealthLossGuards;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.electromaster.ElectromasterSkillMigration;
import org.academy.internal.common.ability.electromaster.skills.lv4.IronSandArsenal;
import org.academy.internal.common.attribute.PlayerAttributeRuntime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Iron sand shares the active category's persisted MP pool without CP debt or recovery suppression. */
public final class IronSandResourceManager implements AbilitySubsystem, IronSandResourceService {
    public static final Identifier DEFENSE_SOURCE = AcademyCraft.academy("iron_sand_defense");
    public static final TagKey<Biome> RICH_BIOMES = TagKey.create(Registries.BIOME, AcademyCraft.academy("iron_sand_rich"));
    private final PlayerDataManager dataManager;
    private final PlayerCPManager cpManager;
    private final SyncManager sync;
    private final Set<UUID> defending = new HashSet<>();
    private final Map<UUID, Long> recoveryTicks = new HashMap<>();

    public IronSandResourceManager(PlayerDataManager dataManager, PlayerCPManager cpManager, SyncManager sync) {
        this.dataManager = dataManager;
        this.cpManager = cpManager;
        this.sync = sync;
    }

    private boolean available(ServerPlayer player) {
        return SkillAvailability.isLearnedAndAvailable(player, Skills.IRON_SAND_ARSENAL.get());
    }

    @Override
    public void onPlayerLogin(ServerPlayer player) {
        var data = dataManager.getData(player.getUUID());
        if (data == null) return;
        if (!data.isElectromasterMigrated()) {
            if (ElectromasterSkillMigration.merge(data.getMutableSkillDataMap())) {
                data.setPersistedSkillEnabled(ElectromasterSkillMigration.IRON_SAND, true);
                data.removePersistedSkillEnabled(ElectromasterSkillMigration.SHIELD);
                sync.schedulePlayerSync(player.getUUID(), SyncTypes.SKILL_DATA);
            }
            data.setElectromasterMigrated(true);
        }
        cpManager.releaseMaintenanceOccupation(player.getUUID(), ElectromasterSkillMigration.SHIELD);
        cpManager.releaseMaintenanceOccupation(player.getUUID(), ElectromasterSkillMigration.IRON_SAND);
        PlayerAttributeRuntime.syncTrueResistanceModifier(player,
                AcademyCraft.academy("electromagnetic_shield_true_resistance"), 0, false);
        setDefense(player, false);
        reconcileCapacity(player);
    }

    @Override
    public void onPlayerLogout(ServerPlayer player) {
        setDefense(player, false);
        recoveryTicks.remove(player.getUUID());
    }

    @Override
    public void tick(ServerPlayer player) {
        reconcileCapacity(player);
        if (!available(player) || !player.isAlive() || player.isSpectator()) {
            setDefense(player, false);
            if (!player.isAlive() && dataManager.getPlayerAbilityCategory(player.getUUID()) == AbilityCategories.ELECTROMASTER.get()) {
                cpManager.setCurrMP(player.getUUID(), 0);
            }
            return;
        }
        var now = (long) player.level().getServer().getTickCount();
        var previous = recoveryTicks.put(player.getUUID(), now);
        if (previous == null || previous != now) {
            account(player).recover(IronSandTuning.recoveryPerTick(cpManager.getAbilityPowerMultiplier(player.getUUID()),
                    player.level().getBiome(player.blockPosition()).is(RICH_BIOMES)));
        }
        if (isDefenseActive(player)) {
            if (!maintainDefense(player)) setDefense(player, false);
            else {
                HealthLossGuards.set(player, DEFENSE_SOURCE, account(player),
                        IronSandTuning.massCostMultiplier(Skills.IRON_SAND_ARSENAL.get().getEffectiveProficiencyMilestone(player)), true);
                IronSandArsenal.Server.interceptNearby(player);
            }
        }
    }

    @Override public void processSync(ServerPlayer player) {}

    @Override
    public void reconcileCapacity(ServerPlayer player) {
        if (dataManager.getPlayerAbilityCategory(player.getUUID()) != AbilityCategories.ELECTROMASTER.get()) return;
        var data = dataManager.getData(player.getUUID());
        if (data == null) return;
        var cp = data.getCpData();
        var capacity = available(player) ? (float) IronSandTuning.capacity(cpManager.getAbilityPowerMultiplier(player.getUUID()),
                Skills.IRON_SAND_ARSENAL.get().getEffectiveProficiencyMilestone(player)) : 0;
        var current = cp.getCurrMP();
        if (capacity > 0 && !data.isIronSandInitialized()) {
            data.setIronSandInitialized(true);
            current = capacity;
        }
        current = Float.isFinite(current) ? Math.clamp(current, 0, capacity) : 0;
        if (cp.getMaxMP() != capacity || cp.getCurrMP() != current) {
            cp.setMaxMP(capacity);
            cp.setCurrMP(current);
            changed(player.getUUID());
        }
    }

    @Override
    public AbilityResourceAccount account(ServerPlayer player) {
        var uuid = player.getUUID();
        return new AbilityResourceAccount() {
            @Override public double current() {
                return dataManager.getPlayerAbilityCategory(uuid) == AbilityCategories.ELECTROMASTER.get()
                        ? cpManager.getCurrMP(uuid) : 0;
            }
            @Override public double capacity() {
                return dataManager.getPlayerAbilityCategory(uuid) == AbilityCategories.ELECTROMASTER.get()
                        ? cpManager.getMaxMP(uuid) : 0;
            }
            @Override public boolean tryConsume(double amount) {
                if (!Double.isFinite(amount) || amount < 0 || amount > current() + 1.0e-6) return false;
                if (amount > 0) {
                    cpManager.setCurrMP(uuid, (float) Math.max(0, current() - amount));
                    changed(uuid);
                }
                return true;
            }
            @Override public double recover(double amount) {
                if (!Double.isFinite(amount) || amount <= 0) return 0;
                var before = current();
                var after = Math.min(capacity(), before + amount);
                if (after > before) {
                    cpManager.setCurrMP(uuid, (float) after);
                    changed(uuid);
                }
                return Math.max(0, after - before);
            }
        };
    }

    public boolean tryCast(ServerPlayer player, Skill skill, float cpCost, float massCost,
                           int iterationTicks, String stackGroup, int stackLimit) {
        if (!available(player)) return false;
        reconcileCapacity(player);
        if (!cpManager.tryOccupationAndConsumeMP(player.getUUID(), cpCost, massCost, skill,
                iterationTicks, false, stackGroup, stackLimit)) return false;
        changed(player.getUUID());
        return true;
    }

    @Override public boolean isDefenseActive(ServerPlayer player) { return defending.contains(player.getUUID()); }

    @Override
    public boolean setDefense(ServerPlayer player, boolean enabled) {
        if (enabled && (!SkillAvailability.requestExecution(player, Skills.IRON_SAND_ARSENAL.get(), false) || !player.isAlive() || player.isSpectator() || !maintainDefense(player))) return false;
        if (enabled) defending.add(player.getUUID());
        else {
            defending.remove(player.getUUID());
            cpManager.releaseMaintenanceOccupation(player.getUUID(), ElectromasterSkillMigration.IRON_SAND);
        }
        HealthLossGuards.set(player, DEFENSE_SOURCE, account(player), IronSandTuning.massCostMultiplier(
                Skills.IRON_SAND_ARSENAL.get().getEffectiveProficiencyMilestone(player)), enabled);
        IronSandArsenal.Server.syncData(player);
        return true;
    }

    private boolean maintainDefense(ServerPlayer player) {
        var skill = Skills.IRON_SAND_ARSENAL.get();
        return AbilitySystemServer.getSystem(player).ensurePermanentOccupation(player.getUUID(),
                skill.adjustProficiencyCost(player, SkillProficiencyProfile.CostKind.MAINTENANCE,
                        IronSandTuning.DEFENSE_MAINTENANCE_CP), skill);
    }

    private void changed(UUID uuid) {
        var data = dataManager.getData(uuid);
        if (data != null) data.markDirty();
        sync.schedulePlayerSync(uuid, SyncTypes.CP_DATA);
    }
}
