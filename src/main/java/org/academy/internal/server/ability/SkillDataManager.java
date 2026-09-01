package org.academy.internal.server.ability;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.*;
import org.academy.api.common.ability.pakcet.SyncSkillDataPacket;
import org.academy.api.common.data.AbilityData;
import org.academy.api.common.registries.Registries;
import org.academy.internal.common.skilldata.SkillData;
import org.misaka.MisakaNetworkServer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class SkillDataManager implements AbilitySubsystem {
    private static final StackWalker STATE_STACK_WALKER = StackWalker.getInstance(
            StackWalker.Option.RETAIN_CLASS_REFERENCE
    );
    private final SyncManager syncManager;
    private final PlayerDataManager playerDataManager;
    private final Map<UUID, Map<String, SkillActivity>> pendingActivities = new ConcurrentHashMap<>();

    private BiConsumer<UUID, Integer> onSkillLevelUp = (_, _) -> {
    };
    private Consumer<UUID> onSkillSetChanged = uuid -> {
    };
    private BiConsumer<UUID, Float> onProficiencyGain = (_, _) -> {
    };

    public SkillDataManager(PlayerDataManager playerDataManager, SyncManager syncManager) {
        this.syncManager = syncManager;
        this.playerDataManager = playerDataManager;
    }

    static SkillActivity strongestActivity(SkillActivity left, SkillActivity right) {
        return left == SkillActivity.EFFECTIVE || right == SkillActivity.EFFECTIVE
                ? SkillActivity.EFFECTIVE : SkillActivity.ACTIVE;
    }

    static ProficiencyEvent resolveContinuousEvent(boolean passive, SkillActivity activity) {
        if (passive) return ProficiencyEvent.PASSIVE_TICK;
        if (activity == SkillActivity.EFFECTIVE) return ProficiencyEvent.EFFECTIVE_TICK;
        if (activity == SkillActivity.ACTIVE) return ProficiencyEvent.ACTIVE_TICK;
        return null;
    }

    private static int applyProficiency(SkillData data, Skill skill, float increment) {
        var oldLevel = skill.getLevelForProficiency(data.getProficiency());
        data.setProficiency(data.getProficiency() + increment);
        var newLevel = skill.getLevelForProficiency(data.getProficiency());
        return Math.max(0, newLevel - oldLevel);
    }

    static int removeCategorySkills(
            Map<String, SkillData> skillDataMap,
            Function<String, SkillScope> scopeResolver
    ) {
        var previousSize = skillDataMap.size();
        skillDataMap.keySet().removeIf(skillId -> scopeResolver.apply(skillId) != SkillScope.COMMON);
        return previousSize - skillDataMap.size();
    }

    private static SkillScope resolveSkillScope(String skillId) {
        var identifier = Identifier.tryParse(skillId);
        if (identifier == null) return null;
        return Registries.SKILLS.get(identifier)
                .map(reference -> reference.value().getScope())
                .orElse(null);
    }

    @Override
    public void onPlayerLogin(ServerPlayer player) {
        var playerData = playerDataManager.getData(player.getUUID());
        if (playerData != null) maintainSkillActivationStates(playerData);
        syncManager.schedulePlayerSync(player.getUUID(), SyncTypes.SKILL_DATA);
    }

    @Override
    public void onPlayerLogout(ServerPlayer player) {
        pendingActivities.remove(player.getUUID());
    }

    @Override
    public void tick(ServerPlayer player) {
        var uuid = player.getUUID();
        var playerData = playerDataManager.getData(uuid);
        if (playerData == null) return;

        var activationStateChanged = maintainSkillActivationStates(playerData);
        var activities = pendingActivities.remove(uuid);
        var category = playerDataManager.getPlayerAbilityCategory(uuid);
        var overload = playerData.getCpData().getStatus() == AbilityData.Status.OVERLOAD;
        var changed = activationStateChanged;
        var crossedThreshold = false;

        for (var entry : playerData.getSkillDataMap().entrySet()) {
            var id = Identifier.tryParse(entry.getKey());
            if (id == null) continue;
            var skill = Registries.SKILLS.get(id).map(reference -> reference.value()).orElse(null);
            if (skill == null) continue;
            var data = entry.getValue();
            if (!data.isEnabled() || overload || !LearningHelper.isSkillAvailableForCategory(category, skill)) continue;

            var effectLevel = skill.getLevelForProficiency(data.getProficiency());
            var activity = activities == null ? null : activities.get(entry.getKey());
            var event = resolveContinuousEvent(skill.isPassive(effectLevel), activity);
            if (event == null) continue;

            var levelsGained = applyProficiency(data, skill, event.getIncrement());
            if (levelsGained > 0) {
                crossedThreshold = true;
                onSkillLevelUp.accept(uuid, levelsGained);
            }
            onProficiencyGain.accept(uuid, event.getIncrement());
            changed = true;
        }

        if (!changed) return;
        playerData.markDirty();
        if (crossedThreshold || player.tickCount % 20 == 0) {
            syncManager.schedulePlayerSync(uuid, SyncTypes.SKILL_DATA);
        }
    }

    @Override
    public void processSync(ServerPlayer player) {
        var playerData = playerDataManager.getData(player.getUUID());
        if (playerData == null) return;
        MisakaNetworkServer.send(player, new SyncSkillDataPacket(playerData.getSkillDataMap()));
    }

    public <T extends SkillData> boolean mutate(
            UUID uuid,
            String skillId,
            Class<T> type,
            Consumer<T> action
    ) {
        var playerData = playerDataManager.getData(uuid);
        if (playerData == null) return false;
        var data = playerData.getSkillDataMap().get(skillId);
        if (!type.isInstance(data)) return false;
        bindSkillActivationState(playerData, skillId, data);

        action.accept(type.cast(data));
        playerData.markDirty();
        syncManager.schedulePlayerSync(uuid, SyncTypes.SKILL_DATA);
        return true;
    }

    private void query(UUID uuid, String skillId, Consumer<SkillData> action) {
        var playerData = playerDataManager.getData(uuid);
        if (playerData == null) return;
        var data = playerData.getSkillDataMap().get(skillId);
        if (data != null) action.accept(data);
    }

    public int getSkillLevel(UUID uuid, String skillKey) {
        var id = Identifier.tryParse(skillKey);
        if (id == null) return 0;
        var skill = Registries.SKILLS.get(id).map(reference -> reference.value()).orElse(null);
        if (skill == null) return 0;
        var result = new int[]{0};
        query(uuid, skillKey, data -> result[0] = skill.getLevelForProficiency(data.getProficiency()));
        return result[0];
    }

    public float getSkillProficiency(UUID uuid, String skillKey) {
        var result = new float[]{0.0f};
        query(uuid, skillKey, data -> result[0] = data.getProficiency());
        return result[0];
    }

    public boolean addSkillProficiency(UUID uuid, Skill skill, ProficiencyEvent event) {
        if (skill == null || event == null) return false;
        return addSkillProficiency(uuid, skill, event.getIncrement());
    }

    public boolean addSkillProficiency(UUID uuid, Skill skill, float increment) {
        if (skill == null || !Float.isFinite(increment) || increment == 0.0f) return false;
        var playerData = playerDataManager.getData(uuid);
        if (playerData == null) return false;
        var data = playerData.getSkillDataMap().get(skill.getKeyString());
        if (data == null) return false;

        var levelsGained = applyProficiency(data, skill, increment);
        if (levelsGained > 0) onSkillLevelUp.accept(uuid, levelsGained);
        if (increment > 0.0f) onProficiencyGain.accept(uuid, increment);
        else onSkillSetChanged.accept(uuid);
        playerData.markDirty();
        syncManager.schedulePlayerSync(uuid, SyncTypes.SKILL_DATA);
        return true;
    }

    public boolean setSkillProficiency(UUID uuid, Skill skill, float proficiency) {
        if (skill == null) return false;
        var playerData = playerDataManager.getData(uuid);
        if (playerData == null) return false;
        var data = playerData.getSkillDataMap().get(skill.getKeyString());
        if (data == null) return false;

        var oldLevel = skill.getLevelForProficiency(data.getProficiency());
        data.setProficiency(proficiency);
        var newLevel = skill.getLevelForProficiency(data.getProficiency());
        if (newLevel > oldLevel) onSkillLevelUp.accept(uuid, newLevel - oldLevel);
        onSkillSetChanged.accept(uuid);
        playerData.markDirty();
        syncManager.schedulePlayerSync(uuid, SyncTypes.SKILL_DATA);
        return true;
    }

    public void reportSkillActivity(UUID uuid, Skill skill, SkillActivity activity) {
        if (uuid == null || skill == null || activity == null) return;
        pendingActivities.computeIfAbsent(uuid, _ -> new ConcurrentHashMap<>())
                .merge(skill.getKeyString(), activity, SkillDataManager::strongestActivity);
    }

    /**
     * @deprecated Use {@link #addSkillProficiency(UUID, Skill, ProficiencyEvent)}.
     */
    @Deprecated
    public void addSkillExp(UUID uuid, Skill skill, ExpEvent event) {
        addSkillProficiency(uuid, skill, event.event);
    }

    /**
     * @deprecated Use {@link #getSkillProficiency(UUID, String)}.
     */
    @Deprecated
    public float getSkillExp(UUID uuid, String skillKey) {
        return getSkillProficiency(uuid, skillKey);
    }

    public void addSkill(ServerPlayer serverPlayer, String skillKey) {
        var uuid = serverPlayer.getUUID();
        var playerData = playerDataManager.getData(uuid);
        if (playerData == null) return;

        Registries.SKILLS.get(Identifier.parse(skillKey)).ifPresent(skillReference -> {
            var skillData = playerData.getMutableSkillDataMap().putIfAbsent(
                    skillKey,
                    skillReference.value().createData()
            );
            if (skillData == null) {
                var addedData = playerData.getSkillDataMap().get(skillKey);
                playerData.restoreRetainedSkillProficiency(skillKey, addedData);
                bindSkillActivationState(playerData, skillKey, addedData);
                playerData.markDirty();
                syncManager.schedulePlayerSync(uuid, SyncTypes.SKILL_DATA);
                onSkillSetChanged.accept(uuid);
            }
        });
    }

    public void removeSkill(UUID uuid, String skillKey) {
        var skillId = Identifier.tryParse(skillKey);
        var skill = skillId == null ? null : Registries.SKILLS.get(skillId)
                .map(reference -> reference.value())
                .orElse(null);
        if (skill == null) return;
        var caller = STATE_STACK_WALKER.walk(frames -> frames
                .dropWhile(frame -> frame.getDeclaringClass() != SkillDataManager.class
                        || !frame.getMethodName().equals("removeSkill"))
                .skip(1)
                .map(StackWalker.StackFrame::getDeclaringClass)
                .findFirst()
                .orElse(null));
        var callerDomain = caller == null ? null : caller.getProtectionDomain();
        var academyDomain = AcademyCraft.class.getProtectionDomain();
        var ownerDomain = skill.getClass().getProtectionDomain();
        var allowed = callerDomain != null
                && (callerDomain == academyDomain || callerDomain == ownerDomain);
        if (!allowed && callerDomain != null && callerDomain.getCodeSource() != null) {
            var callerLocation = callerDomain.getCodeSource().getLocation();
            var academyLocation = academyDomain == null || academyDomain.getCodeSource() == null
                    ? null : academyDomain.getCodeSource().getLocation();
            var ownerLocation = ownerDomain == null || ownerDomain.getCodeSource() == null
                    ? null : ownerDomain.getCodeSource().getLocation();
            allowed = callerLocation != null
                    && (callerLocation.equals(academyLocation) || callerLocation.equals(ownerLocation));
        }
        if (!allowed) return;

        var playerData = playerDataManager.getData(uuid);
        if (playerData == null) return;
        var removed = playerData.getMutableSkillDataMap().remove(skillKey);
        if (removed == null) return;
        playerData.removePersistedSkillEnabled(skillKey);
        if (resolveSkillScope(skillKey) != SkillScope.COMMON) {
            playerData.retainSkillProficiency(skillKey, removed);
        }
        playerData.markDirty();
        syncManager.schedulePlayerSync(uuid, SyncTypes.SKILL_DATA);
        onSkillSetChanged.accept(uuid);
    }

    public void clearCategorySkills(UUID uuid) {
        var caller = STATE_STACK_WALKER.walk(frames -> frames
                .dropWhile(frame -> frame.getDeclaringClass() != SkillDataManager.class
                        || !frame.getMethodName().equals("clearCategorySkills"))
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

        var playerData = playerDataManager.getData(uuid);
        if (playerData == null) return;
        var removedSkillIds = playerData.getSkillDataMap().keySet().stream()
                .filter(skillId -> resolveSkillScope(skillId) != SkillScope.COMMON)
                .toList();
        playerData.getSkillDataMap().forEach((skillId, data) -> {
            if (resolveSkillScope(skillId) != SkillScope.COMMON) {
                playerData.retainSkillProficiency(skillId, data);
            }
        });
        var removed = removeCategorySkills(
                playerData.getMutableSkillDataMap(),
                SkillDataManager::resolveSkillScope
        );
        if (removed == 0) return;
        removedSkillIds.forEach(playerData::removePersistedSkillEnabled);
        playerData.markDirty();
        syncManager.schedulePlayerSync(uuid, SyncTypes.SKILL_DATA);
        onSkillSetChanged.accept(uuid);
    }

    public void toggleSkill(UUID uuid, String skillId) {
        var id = Identifier.tryParse(skillId);
        var skill = id == null ? null : Registries.SKILLS.get(id)
                .map(reference -> reference.value())
                .orElse(null);
        if (skill == null) return;
        var caller = STATE_STACK_WALKER.walk(frames -> frames
                .dropWhile(frame -> frame.getDeclaringClass() != SkillDataManager.class
                        || !frame.getMethodName().equals("toggleSkill"))
                .skip(1)
                .map(StackWalker.StackFrame::getDeclaringClass)
                .findFirst()
                .orElse(null));
        var callerDomain = caller == null ? null : caller.getProtectionDomain();
        var academyDomain = AcademyCraft.class.getProtectionDomain();
        var ownerDomain = skill.getClass().getProtectionDomain();
        var allowed = callerDomain != null
                && (callerDomain == academyDomain || callerDomain == ownerDomain);
        if (!allowed && callerDomain != null && callerDomain.getCodeSource() != null) {
            var callerLocation = callerDomain.getCodeSource().getLocation();
            var academyLocation = academyDomain == null || academyDomain.getCodeSource() == null
                    ? null : academyDomain.getCodeSource().getLocation();
            var ownerLocation = ownerDomain == null || ownerDomain.getCodeSource() == null
                    ? null : ownerDomain.getCodeSource().getLocation();
            allowed = callerLocation != null
                    && (callerLocation.equals(academyLocation) || callerLocation.equals(ownerLocation));
        }
        if (!allowed) return;

        mutate(uuid, skillId, SkillData.class, SkillData::toggleEnabled);
    }

    private boolean maintainSkillActivationStates(
            org.academy.internal.server.world.level.storage.Player playerData
    ) {
        var changed = false;
        for (var entry : playerData.getSkillDataMap().entrySet()) {
            var data = entry.getValue();
            if (data == null) continue;
            changed |= bindSkillActivationState(playerData, entry.getKey(), data);
        }
        return changed | playerData.consumeSkillActivationSyncDirty();
    }

    private boolean bindSkillActivationState(
            org.academy.internal.server.world.level.storage.Player playerData,
            String skillId,
            SkillData data
    ) {
        var id = Identifier.tryParse(skillId);
        if (id == null) return false;
        var skill = Registries.SKILLS.get(id).map(reference -> reference.value()).orElse(null);
        if (skill == null) return false;

        if (!data.isActivationProtectedFor(skill.getClass())) {
            data.bindActivationProtection(
                    skill.getClass(),
                    enabled -> playerData.setPersistedSkillEnabled(skillId, enabled)
            );
        }

        var persisted = playerData.getPersistedSkillEnabled(skillId);
        if (persisted.isEmpty()) {
            return playerData.setPersistedSkillEnabled(skillId, data.isEnabled());
        }
        if (data.isEnabled() == persisted.get()) return false;
        data.applyPersistedEnabled(persisted.get());
        if (data.isEnabled() != persisted.get()) return false;
        playerData.markDirty();
        return true;
    }

    public void setOnSkillLevelUp(BiConsumer<UUID, Integer> onSkillLevelUp) {
        this.onSkillLevelUp = onSkillLevelUp;
    }

    public void setOnSkillSetChanged(Consumer<UUID> onSkillSetChanged) {
        this.onSkillSetChanged = onSkillSetChanged;
    }

    public void setOnProficiencyGain(BiConsumer<UUID, Float> onProficiencyGain) {
        this.onProficiencyGain = onProficiencyGain;
    }

    /**
     * @deprecated Use {@link ProficiencyEvent}.
     */
    @Deprecated
    public enum ExpEvent {
        KILL_ENTITY(ProficiencyEvent.KILL_ENTITY),
        HIT_ENTITY(ProficiencyEvent.EFFECTIVE_TICK),
        ACT_EFFECTIVE(ProficiencyEvent.TRIGGER),
        TICK_PASSIVE(ProficiencyEvent.PASSIVE_TICK);

        private final ProficiencyEvent event;

        ExpEvent(ProficiencyEvent event) {
            this.event = event;
        }

        public float getIncrement() {
            return event.getIncrement();
        }
    }
}
