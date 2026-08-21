package org.academy.internal.common.ability.mentalout;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.api.common.entitycontrol.*;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.ServerContext;
import org.academy.api.common.ability.SkillProficiencyProfile;
import org.academy.AcademyCraft;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.mentalout.control.MentalControlRuntime;
import org.academy.internal.common.world.damagesource.FriendlyFireSetting;

import java.util.*;

public final class MentaloutControlContext extends ServerContext {
    public static final int CONTROL_PRIORITY = 100;
    private static final int DYNAMIC_SYNC_INTERVAL = 10;
    private static final int DYNAMIC_SYNC_BUDGET = 8;
    private static final Identifier IMPRESSION_ATTACK_SPEED_ID = Identifier.fromNamespaceAndPath(
            AcademyCraft.MOD_ID, "impression_proficiency_attack_speed");
    private static final Identifier IMPRESSION_MOVEMENT_SPEED_ID = Identifier.fromNamespaceAndPath(
            AcademyCraft.MOD_ID, "impression_proficiency_movement_speed");

    private static final byte SUPPORT_FULL = 0;
    private static final byte SUPPORT_BEST_EFFORT = 1;
    private static final byte SUPPORT_UNSUPPORTED = 2;
    private static final byte FLAG_STUPOR = 1;
    private static final byte FLAG_IMPRESSION = 1 << 1;
    private static final byte FLAG_MISIDENTIFICATION = 1 << 2;
    private static final byte FLAG_OVERRIDDEN = 1 << 3;
    private static final byte FLAG_PROTECTED = 1 << 4;
    private static final byte FLAG_RESISTANT = 1 << 5;

    private static final Map<UUID, MentaloutControlContext> BY_CONTROLLER = new HashMap<>();
    private static final Map<UUID, Set<MentaloutControlContext>> BY_SUBJECT = new HashMap<>();
    private static final Map<UUID, Set<MentaloutControlContext>> BY_MISIDENTIFICATION_TARGET = new HashMap<>();
    private static final Map<UUID, Long> REVISIONS = new HashMap<>();

    private final LinkedHashMap<UUID, Entry> entries = new LinkedHashMap<>();
    private boolean stuporEnabled;
    private boolean impressionEnabled;
    private UUID misidentificationTargetUuid;
    private boolean misidentificationAutoTransferred;
    private boolean ended;
    private int syncTicker;
    private int syncCursor;

    private MentaloutControlContext(ServerPlayer player) {
        super(player);
    }

    public static MentaloutControlContext get(ServerPlayer player) {
        return BY_CONTROLLER.get(player.getUUID());
    }

    public static List<LivingEntity> subjects(ServerPlayer player) {
        var context = get(player);
        if (context == null) return List.of();
        return context.entries.values().stream().map(entry -> entry.subject).toList();
    }

    public static void releaseInterventionSubjects(ServerPlayer controller, Set<UUID> subjectIds) {
        if (controller == null || subjectIds == null || subjectIds.isEmpty()) return;
        var context = get(controller);
        if (context == null) return;
        var released = subjectIds.stream()
                .map(context.entries::get)
                .filter(Objects::nonNull)
                .map(entry -> entry.subject)
                .toList();
        for (var subject : released) {
            MentalControlMemory.forget(controller, subject);
            context.remove(subject.getUUID());
        }
    }

    public static void releaseController(UUID controllerUuid) {
        var context = BY_CONTROLLER.get(controllerUuid);
        if (context != null) context.unregister();
    }

    public static void releaseSubject(UUID subjectUuid) {
        var contexts = BY_SUBJECT.get(subjectUuid);
        if (contexts == null) return;
        List.copyOf(contexts).forEach(context -> context.remove(subjectUuid));
    }

    public static void releaseMisidentificationTarget(UUID targetUuid) {
        var contexts = BY_MISIDENTIFICATION_TARGET.get(targetUuid);
        if (contexts == null) return;
        List.copyOf(contexts).forEach(context -> context.clearTargetMisidentification(true));
    }

    public static void onMisidentificationTargetDeath(LivingEntity target) {
        if (target == null) return;
        var contexts = BY_MISIDENTIFICATION_TARGET.get(target.getUUID());
        if (contexts == null) return;
        List.copyOf(contexts).forEach(context -> context.transferMisidentificationTarget(target));
    }

    public static void clearAll() {
        List.copyOf(BY_CONTROLLER.values()).forEach(MentaloutControlContext::unregister);
        BY_CONTROLLER.clear();
        BY_SUBJECT.clear();
        BY_MISIDENTIFICATION_TARGET.clear();
        REVISIONS.clear();
    }

    public static ToggleResult toggleTarget(ServerPlayer player, LivingEntity target) {
        var existing = get(player);
        if (existing != null && target != null && existing.entries.containsKey(target.getUUID())) {
            MentalControlMemory.forget(player, target);
            existing.remove(target.getUUID());
            return ToggleResult.REMOVED;
        }
        if (!MentaloutTargetValidation.isValidRosterTarget(player, target)) {
            return ToggleResult.INVALID;
        }
        if (!supportsAnyControl(target)) return ToggleResult.UNSUPPORTED;

        var context = existing == null ? new MentaloutControlContext(player) : existing;
        var added = context.add(target);
        if (added != ToggleResult.ADDED || existing != null) return added;

        registerNewContext(player, context, target);
        return ToggleResult.ADDED;
    }

    public static ToggleResult recallTarget(ServerPlayer player, Mob mob) {
        if (player == null || mob == null) return ToggleResult.INVALID;
        var existing = get(player);
        if (existing != null && existing.entries.containsKey(mob.getUUID())) return ToggleResult.INVALID;
        if (!MentaloutTargetValidation.isValidRosterTarget(player, mob)) return ToggleResult.INVALID;
        if (!supportsAnyControl(mob)) return ToggleResult.UNSUPPORTED;

        var context = existing == null ? new MentaloutControlContext(player) : existing;
        var added = context.add(mob);
        if (added == ToggleResult.ADDED && existing == null) {
            registerNewContext(player, context, mob);
        }
        return added;
    }

    private static void registerNewContext(
            ServerPlayer player,
            MentaloutControlContext context,
            LivingEntity firstSubject
    ) {
        BY_CONTROLLER.put(player.getUUID(), context);
        AbilitySystemServer.registerContext(context);
        context.sendUpsert(context.entries.get(firstSubject.getUUID()));
    }

    public static boolean isImpressionAlly(UUID controllerUuid, UUID subjectUuid, UUID otherUuid) {
        if (controllerUuid == null || subjectUuid == null || otherUuid == null) return false;
        var context = BY_CONTROLLER.get(controllerUuid);
        if (context == null || !context.impressionEnabled) return false;
        var subject = context.entries.get(subjectUuid);
        if (subject == null || subject.impression == null || subject.impression.isClosed()) return false;
        if (controllerUuid.equals(otherUuid)) return true;
        return context.entries.containsKey(otherUuid);
    }

    public static void handleResync(ServerPlayer player, long clientRevision) {
        var context = get(player);
        if (context == null) {
            MentaloutRosterPackets.sendClear(player, currentRevision(player.getUUID()));
            return;
        }
        context.sendFull();
    }

    private static byte supportLevel(LivingEntity subject) {
        var supported = 0;
        var allFull = true;
        for (var capability : List.of(
                ControlCapability.FORCE_TARGET,
                ControlCapability.FREEZE_AI,
                ControlCapability.RELATION_CONTROL
        )) {
            var evaluation = MentalControlApi.evaluate(subject, capability);
            if (!evaluation.supported()) {
                allFull = false;
                continue;
            }
            supported++;
            if (evaluation.support() != ControlSupport.FULL) allFull = false;
        }
        if (supported == 0) return SUPPORT_UNSUPPORTED;
        return supported == 3 && allFull ? SUPPORT_FULL : SUPPORT_BEST_EFFORT;
    }

    private static float quantize(float value, float step) {
        if (!Float.isFinite(value)) return value;
        return Math.round(value / step) * step;
    }

    private static boolean supportsAnyControl(LivingEntity subject) {
        return supportLevel(subject) != SUPPORT_UNSUPPORTED;
    }

    private static boolean isOverridden(Entry entry) {
        return isOverridden(entry.subject, ControlCapability.FREEZE_AI, entry.stupor)
                || isOverridden(entry.subject, ControlCapability.RELATION_CONTROL, entry.impression)
                || isOverridden(entry.subject, ControlCapability.FORCE_TARGET, entry.misidentification);
    }

    private static boolean isOverridden(
            LivingEntity subject,
            ControlCapability capability,
            ControlHandle handle
    ) {
        if (handle == null || handle.isClosed()) return false;
        return MentalControlApi.inspect(subject, capability)
                .map(inspection -> !inspection.leaseId().equals(handle.id()))
                .orElse(true);
    }

    private static long nextRevision(UUID controllerUuid) {
        var revision = currentRevision(controllerUuid) + 1L;
        REVISIONS.put(controllerUuid, revision);
        return revision;
    }

    private static long currentRevision(UUID controllerUuid) {
        return REVISIONS.getOrDefault(controllerUuid, 0L);
    }

    private static void close(ControlHandle handle) {
        if (handle != null) handle.close();
    }

    public boolean hasEntries() {
        return !entries.isEmpty();
    }

    public boolean contains(UUID subjectId) {
        return subjectId != null && entries.containsKey(subjectId);
    }

    public boolean isStuporEnabled() {
        return stuporEnabled;
    }

    public boolean isImpressionEnabled() {
        return impressionEnabled;
    }

    public boolean isTargetMisidentificationTarget(LivingEntity target) {
        return target != null && target.getUUID().equals(misidentificationTargetUuid);
    }

    public BatchResult applyTargetMisidentification(LivingEntity forcedTarget) {
        if (ended || forcedTarget == null || !MentaloutTargetValidation.isValidForcedTarget(player, forcedTarget)) {
            return BatchResult.NONE;
        }
        if (isTargetMisidentificationTarget(forcedTarget)) {
            clearTargetMisidentification(true);
            return BatchResult.NONE;
        }
        var candidates = entries.values().stream()
                .filter(entry -> entry.subject != forcedTarget)
                .filter(entry -> MentalControlApi.supports(entry.subject, ControlCapability.FORCE_TARGET))
                .toList();
        if (candidates.isEmpty()) return new BatchResult(0, entries.size(), 0, false, false);

        var skill = Skills.TARGET_MISIDENTIFICATION.get();
        var castCost = skill.adjustProficiencyCost(
                player,
                SkillProficiencyProfile.CostKind.CAST,
                MentaloutConfig.targetMisidentificationCost(player)
        );
        var result = new int[2];
        var attempted = new boolean[1];
        var replacements = new HashMap<Entry, ControlHandle>();
        var cast = AbilitySystemServer.getSystem(player).castCpIfActionSucceeds(
                player,
                skill,
                castCost,
                () -> {
                    attempted[0] = true;
                    for (var entry : candidates) {
                        try {
                            var handle = MentalControlApi.apply(ControlRequest.permanent(
                                    player,
                                    entry.subject,
                                    skill.getKey(),
                                    CONTROL_PRIORITY,
                                    List.of(new ControlDirective.ForceTarget(forcedTarget.getUUID()))
                            ));
                            replacements.put(entry, handle);
                            result[0]++;
                        } catch (RuntimeException exception) {
                            result[1]++;
                        }
                    }
                    return result[0] > 0;
                }
        );
        if (!cast) {
            replacements.values().forEach(MentaloutControlContext::close);
            return new BatchResult(
                    0,
                    entries.size() - candidates.size(),
                    result[1],
                    false,
                    !attempted[0]
            );
        }
        for (var entry : entries.values()) {
            var replacement = replacements.get(entry);
            close(entry.misidentification);
            entry.misidentification = replacement;
        }
        setMisidentificationTarget(forcedTarget.getUUID());
        misidentificationAutoTransferred = false;
        sendFullUpdate();
        return new BatchResult(
                result[0],
                entries.size() - candidates.size(),
                result[1],
                result[0] > 0,
                false
        );
    }

    public BatchResult toggleStupor() {
        if (ended || entries.isEmpty()) return BatchResult.NONE;
        if (stuporEnabled) {
            stuporEnabled = false;
            for (var entry : entries.values()) {
                close(entry.stupor);
                entry.stupor = null;
                entry.stuporStartedAt = Long.MAX_VALUE;
                if (entry.subject instanceof ServerPlayer subject) {
                    PlayerControlSessionManager.grantResistance(subject);
                }
            }
            AbilitySystemServer.getSystem(player).releaseMaintenanceOccupation(
                    player.getUUID(),
                    Skills.MENTAL_STUPOR.get().getKeyString()
            );
            sendFullUpdate();
            return new BatchResult(0, 0, 0, false, false);
        }

        var candidates = entries.values().stream()
                .filter(entry -> MentalControlApi.supports(entry.subject, ControlCapability.FREEZE_AI))
                .toList();
        if (candidates.isEmpty()) return new BatchResult(0, entries.size(), 0, false, false);
        var expectedCost = candidates.stream().mapToDouble(this::stuporCost).sum();
        var system = AbilitySystemServer.getSystem(player);
        var skill = Skills.MENTAL_STUPOR.get();
        if (!system.canCastWithPermanentOccupations(
                player,
                skill,
                0.0f,
                Map.of(skill, (float) expectedCost)
        )) {
            return new BatchResult(0, entries.size() - candidates.size(), 0, false, true);
        }

        var applied = 0;
        var failed = 0;
        var activated = new ArrayList<Entry>();
        for (var entry : candidates) {
            try {
                entry.stupor = applyPermanent(entry.subject, skill.getKey(),
                        new ControlDirective.FreezeAi());
                entry.stuporStartedAt = player.level().getGameTime();
                activated.add(entry);
                applied++;
            } catch (RuntimeException exception) {
                failed++;
            }
        }
        if (applied > 0 && !system.replacePermanentOccupation(
                player.getUUID(),
                currentStuporCost(),
                skill
        )) {
            for (var entry : activated) {
                close(entry.stupor);
                entry.stupor = null;
                entry.stuporStartedAt = Long.MAX_VALUE;
            }
            sendFullUpdate();
            return new BatchResult(
                    0,
                    entries.size() - candidates.size(),
                    failed + applied,
                    false,
                    true
            );
        }
        stuporEnabled = applied > 0;
        sendFullUpdate();
        return new BatchResult(applied, entries.size() - candidates.size(), failed, stuporEnabled, false);
    }

    public BatchResult toggleImpression() {
        if (ended || entries.isEmpty()) return BatchResult.NONE;
        if (impressionEnabled) {
            impressionEnabled = false;
            for (var entry : entries.values()) {
                close(entry.impression);
                entry.impression = null;
                entry.removeImpressionBuff();
            }
            AbilitySystemServer.getSystem(player).releaseMaintenanceOccupation(
                    player.getUUID(),
                    Skills.IMPRESSION_MANIPULATION.get().getKeyString()
            );
            sendFullUpdate();
            return new BatchResult(0, 0, 0, false, false);
        }

        var candidates = entries.values().stream()
                .filter(entry -> MentalControlApi.supports(entry.subject, ControlCapability.RELATION_CONTROL))
                .toList();
        if (candidates.isEmpty()) return new BatchResult(0, entries.size(), 0, false, false);
        var expectedCost = candidates.stream().mapToDouble(this::impressionCost).sum();
        var system = AbilitySystemServer.getSystem(player);
        var skill = Skills.IMPRESSION_MANIPULATION.get();
        if (!system.canCastWithPermanentOccupations(
                player,
                skill,
                0.0f,
                Map.of(skill, (float) expectedCost)
        )) {
            return new BatchResult(0, entries.size() - candidates.size(), 0, false, true);
        }

        var applied = 0;
        var failed = 0;
        var activated = new ArrayList<Entry>();
        for (var entry : candidates) {
            try {
                entry.impression = applyPermanent(
                        entry.subject,
                        skill.getKey(),
                        new ControlDirective.ImpressionAlliance()
                );
                activated.add(entry);
                applied++;
            } catch (RuntimeException exception) {
                failed++;
            }
        }
        if (applied > 0 && !system.replacePermanentOccupation(
                player.getUUID(),
                currentImpressionCost(),
                skill
        )) {
            for (var entry : activated) {
                close(entry.impression);
                entry.impression = null;
                entry.removeImpressionBuff();
            }
            sendFullUpdate();
            return new BatchResult(
                    0,
                    entries.size() - candidates.size(),
                    failed + applied,
                    false,
                    true
            );
        }
        impressionEnabled = applied > 0;
        sendFullUpdate();
        return new BatchResult(applied, entries.size() - candidates.size(), failed, impressionEnabled, false);
    }

    @SubscribeEvent
    public void onTick(ServerTickEvent.Pre event) {
        if (ended) return;
        if (player.hasDisconnected() || !player.isAlive() || !Skills.MENTAL_INTERVENTION.get().isEnabled(player)) {
            unregister();
            return;
        }
        Skills.MENTAL_INTERVENTION.get().reportActivity(player, !entries.isEmpty());
        if (misidentificationTargetUuid != null
                || entries.values().stream().anyMatch(entry -> entry.misidentification != null)) {
            Skills.TARGET_MISIDENTIFICATION.get().reportActivity(player, true);
        }
        if (stuporEnabled) Skills.MENTAL_STUPOR.get().reportActivity(player, true);
        if (impressionEnabled) Skills.IMPRESSION_MANIPULATION.get().reportActivity(player, true);

        var hasMisidentificationState = misidentificationTargetUuid != null
                || entries.values().stream().anyMatch(entry -> entry.misidentification != null);
        var clearMisidentification = hasMisidentificationState
                && (!Skills.TARGET_MISIDENTIFICATION.get().isEnabled(player)
                || !isMisidentificationTargetRetained());
        var clearStupor = stuporEnabled && !Skills.MENTAL_STUPOR.get().isEnabled(player);
        var clearImpression = impressionEnabled && !Skills.IMPRESSION_MANIPULATION.get().isEnabled(player);
        var batchCleanup = clearMisidentification || clearStupor || clearImpression;
        if (clearStupor) stuporEnabled = false;
        if (clearImpression) impressionEnabled = false;
        var occupationsChanged = clearStupor || clearImpression;
        var changedEntries = new ArrayList<Entry>();
        for (var entry : List.copyOf(entries.values())) {
            if (!MentaloutTargetValidation.isRetained(player, entry.subject)) {
                remove(entry.subject.getUUID());
                continue;
            }
            var changed = false;
            if (entry.misidentification != null && (clearMisidentification
                    || entry.misidentification.isClosed())) {
                close(entry.misidentification);
                entry.misidentification = null;
                changed = true;
            }
            if (entry.stupor != null && (clearStupor || entry.stupor.isClosed())) {
                close(entry.stupor);
                entry.stupor = null;
                entry.stuporStartedAt = Long.MAX_VALUE;
                if (entry.subject instanceof ServerPlayer subject) {
                    PlayerControlSessionManager.grantResistance(subject);
                }
                changed = true;
                occupationsChanged = true;
            }
            if (entry.impression != null && (clearImpression || entry.impression.isClosed())) {
                close(entry.impression);
                entry.impression = null;
                entry.removeImpressionBuff();
                changed = true;
                occupationsChanged = true;
            }
            if (changed) changedEntries.add(entry);
        }
        if (clearMisidentification) detachMisidentificationTarget();
        if (ended) return;
        updateDeepStupor();
        updateImpressionFormationBuffs(clearImpression);
        if (occupationsChanged) replacePermanentOccupations();
        var sentImmediateSync = batchCleanup || !changedEntries.isEmpty();
        if (batchCleanup || changedEntries.size() > DYNAMIC_SYNC_BUDGET) {
            sendFullUpdate();
        } else {
            changedEntries.forEach(this::sendUpsert);
        }

        syncTicker++;
        if (syncTicker >= DYNAMIC_SYNC_INTERVAL) {
            syncTicker = 0;
            if (!sentImmediateSync) syncDynamicWindow();
        }
    }

    private ToggleResult add(LivingEntity subject) {
        var entry = new Entry(subject, costWeight(subject));
        var applyStupor = stuporEnabled
                && MentalControlApi.supports(subject, ControlCapability.FREEZE_AI);
        var applyImpression = impressionEnabled
                && MentalControlApi.supports(subject, ControlCapability.RELATION_CONTROL);
        var futureStuporCost = currentStuporCost() + (applyStupor ? stuporCost(entry) : 0.0f);
        var futureImpressionCost = currentImpressionCost()
                + (applyImpression ? impressionCost(entry) : 0.0f);
        var permanentAmounts = Map.of(
                Skills.MENTAL_STUPOR.get(), futureStuporCost,
                Skills.IMPRESSION_MANIPULATION.get(), futureImpressionCost
        );
        var system = AbilitySystemServer.getSystem(player);
        if (!system.canCastWithPermanentOccupations(
                player,
                Skills.MENTAL_INTERVENTION.get(),
                Skills.MENTAL_INTERVENTION.get().adjustProficiencyCost(
                        player, SkillProficiencyProfile.CostKind.CAST,
                        MentaloutConfig.mentalInterventionCost(player)),
                permanentAmounts
        )) {
            return ToggleResult.INSUFFICIENT_CP;
        }

        try {
            if (applyStupor) {
                entry.stupor = applyPermanent(subject, Skills.MENTAL_STUPOR.get().getKey(),
                        new ControlDirective.FreezeAi());
                entry.stuporStartedAt = player.level().getGameTime();
            }
            if (applyImpression) {
                entry.impression = applyPermanent(subject, Skills.IMPRESSION_MANIPULATION.get().getKey(),
                        new ControlDirective.ImpressionAlliance());
            }
        } catch (RuntimeException exception) {
            entry.closeAll();
            return ToggleResult.UNSUPPORTED;
        }

        var occupied = system.castWithPermanentOccupations(
                player,
                Skills.MENTAL_INTERVENTION.get(),
                Skills.MENTAL_INTERVENTION.get().adjustProficiencyCost(
                        player, SkillProficiencyProfile.CostKind.CAST,
                        MentaloutConfig.mentalInterventionCost(player)),
                permanentAmounts
        );
        if (!occupied) {
            entry.closeAll();
            return ToggleResult.INSUFFICIENT_CP;
        }

        entries.put(subject.getUUID(), entry);
        BY_SUBJECT.computeIfAbsent(subject.getUUID(), _ -> new HashSet<>()).add(this);
        if (subject instanceof Mob mob) {
            MentalControlMemory.remember(player, mob);
            MentalControlRecall.allow(player, mob);
        }
        if (BY_CONTROLLER.containsKey(player.getUUID())) sendUpsert(entry);
        return ToggleResult.ADDED;
    }

    private void remove(UUID targetUuid) {
        var entry = entries.remove(targetUuid);
        if (entry == null) return;
        entry.closeAll();
        var owners = BY_SUBJECT.get(targetUuid);
        if (owners != null) {
            owners.remove(this);
            if (owners.isEmpty()) BY_SUBJECT.remove(targetUuid);
        }
        replacePermanentOccupations();
        if (entries.isEmpty()) {
            stuporEnabled = false;
            impressionEnabled = false;
            unregister();
        } else {
            sendRemove(targetUuid);
        }
    }

    private void replacePermanentOccupations() {
        if (ended) return;
        AbilitySystemServer.getSystem(player).replacePermanentOccupations(
                player.getUUID(),
                Map.of(
                        Skills.MENTAL_STUPOR.get(), currentStuporCost(),
                        Skills.IMPRESSION_MANIPULATION.get(), currentImpressionCost()
                )
        );
    }

    private float currentStuporCost() {
        var cost = 0.0f;
        for (var entry : entries.values()) {
            if (entry.stupor != null && !entry.stupor.isClosed()) cost += stuporCost(entry);
        }
        return cost;
    }

    private float currentImpressionCost() {
        var cost = 0.0f;
        for (var entry : entries.values()) {
            if (entry.impression != null && !entry.impression.isClosed()) cost += impressionCost(entry);
        }
        return cost;
    }

    private float stuporCost(Entry entry) {
        return entry.costWeight * Skills.MENTAL_STUPOR.get().adjustProficiencyCost(
                player, SkillProficiencyProfile.CostKind.DYNAMIC,
                MentaloutConfig.mentalStuporCost(player));
    }

    private float impressionCost(Entry entry) {
        return entry.costWeight * Skills.IMPRESSION_MANIPULATION.get().adjustProficiencyCost(
                player, SkillProficiencyProfile.CostKind.DYNAMIC,
                MentaloutConfig.impressionManipulationCost(player));
    }

    private float costWeight(LivingEntity subject) {
        return MentaloutControlCost.multiplier(player, subject);
    }

    private ControlHandle applyPermanent(
            LivingEntity subject,
            Identifier source,
            ControlDirective directive
    ) {
        return MentalControlApi.apply(ControlRequest.permanent(
                player,
                subject,
                source,
                CONTROL_PRIORITY,
                List.of(directive)
        ));
    }

    private boolean isMisidentificationTargetRetained() {
        if (misidentificationTargetUuid == null) return false;
        var target = player.level().getEntity(misidentificationTargetUuid);
        return target instanceof LivingEntity living && living.isAlive() && !living.isRemoved();
    }

    private void setMisidentificationTarget(UUID targetUuid) {
        detachMisidentificationTarget();
        misidentificationTargetUuid = targetUuid;
        BY_MISIDENTIFICATION_TARGET.computeIfAbsent(targetUuid, _ -> new HashSet<>()).add(this);
    }

    private void transferMisidentificationTarget(LivingEntity previousTarget) {
        var skill = Skills.TARGET_MISIDENTIFICATION.get();
        if (ended || misidentificationAutoTransferred
                || !skill.isEnabled(player) || !skill.hasProficiencyMilestone(player, 3)) {
            clearTargetMisidentification(true);
            return;
        }
        var replacementTarget = player.level().getEntitiesOfClass(
                        LivingEntity.class,
                        previousTarget.getBoundingBox().inflate(8.0),
                        candidate -> candidate != player && candidate != previousTarget
                                && candidate.isAlive() && !candidate.isRemoved()
                                && !entries.containsKey(candidate.getUUID())
                                && !FriendlyFireSetting.shouldPrevent(player, candidate))
                .stream()
                .min(java.util.Comparator.comparingDouble(candidate ->
                        candidate.distanceToSqr(previousTarget)))
                .orElse(null);
        if (replacementTarget == null) {
            clearTargetMisidentification(true);
            return;
        }
        var replacements = new HashMap<Entry, ControlHandle>();
        for (var entry : entries.values()) {
            if (entry.misidentification == null || entry.misidentification.isClosed()) continue;
            try {
                replacements.put(entry, applyPermanent(
                        entry.subject,
                        skill.getKey(),
                        new ControlDirective.ForceTarget(replacementTarget.getUUID())
                ));
            } catch (RuntimeException ignored) {
            }
        }
        for (var entry : entries.values()) {
            close(entry.misidentification);
            entry.misidentification = replacements.get(entry);
        }
        if (replacements.isEmpty()) {
            clearTargetMisidentification(true);
            return;
        }
        setMisidentificationTarget(replacementTarget.getUUID());
        misidentificationAutoTransferred = true;
        sendFullUpdate();
    }

    private void updateDeepStupor() {
        if (!stuporEnabled || !Skills.MENTAL_STUPOR.get().hasProficiencyMilestone(player, 3)) return;
        var now = player.level().getGameTime();
        for (var entry : entries.values()) {
            if (entry.subject instanceof ServerPlayer || entry.stupor == null || entry.stupor.isClosed()
                    || now - entry.stuporStartedAt < 60L) continue;
            entry.subject.setSprinting(false);
            if (entry.subject.isUsingItem()) entry.subject.stopUsingItem();
        }
    }

    private void updateImpressionFormationBuffs(boolean clearImmediately) {
        var now = player.level().getGameTime();
        if (clearImmediately || !impressionEnabled
                || !Skills.IMPRESSION_MANIPULATION.get().hasProficiencyMilestone(player, 3)) {
            entries.values().forEach(Entry::removeImpressionBuff);
            return;
        }
        var targetCounts = new HashMap<UUID, Integer>();
        for (var entry : entries.values()) {
            if (entry.impression == null || entry.impression.isClosed()
                    || !(entry.subject instanceof Mob mob) || mob.getTarget() == null) continue;
            var target = mob.getTarget();
            if (!target.isAlive() || FriendlyFireSetting.shouldPrevent(player, target)) continue;
            targetCounts.merge(target.getUUID(), 1, Integer::sum);
        }
        for (var entry : entries.values()) {
            var qualifies = entry.impression != null && !entry.impression.isClosed()
                    && entry.subject instanceof Mob mob && mob.getTarget() != null
                    && targetCounts.getOrDefault(mob.getTarget().getUUID(), 0) >= 2;
            if (qualifies) entry.impressionBuffUntil = now + 60L;
            if (entry.impressionBuffUntil > now) entry.applyImpressionBuff();
            else entry.removeImpressionBuff();
        }
    }

    private void detachMisidentificationTarget() {
        if (misidentificationTargetUuid == null) return;
        var contexts = BY_MISIDENTIFICATION_TARGET.get(misidentificationTargetUuid);
        if (contexts != null) {
            contexts.remove(this);
            if (contexts.isEmpty()) BY_MISIDENTIFICATION_TARGET.remove(misidentificationTargetUuid);
        }
        misidentificationTargetUuid = null;
    }

    private boolean clearTargetMisidentification(boolean sync) {
        var changed = misidentificationTargetUuid != null;
        for (var entry : entries.values()) {
            if (entry.misidentification == null) continue;
            close(entry.misidentification);
            entry.misidentification = null;
            changed = true;
        }
        detachMisidentificationTarget();
        misidentificationAutoTransferred = false;
        if (changed && sync && !ended && BY_CONTROLLER.get(player.getUUID()) == this) sendFullUpdate();
        return changed;
    }

    private void sendUpsertIfChanged(Entry entry) {
        var packetEntry = packetEntry(entry);
        var fingerprint = packetEntry.hashCode();
        if (fingerprint == entry.lastFingerprint) return;
        sendUpsert(entry, packetEntry, fingerprint);
    }

    private void syncDynamicWindow() {
        if (entries.isEmpty()) return;
        var window = List.copyOf(entries.values());
        if (syncCursor >= window.size()) syncCursor = 0;
        var count = Math.min(DYNAMIC_SYNC_BUDGET, window.size());
        for (var offset = 0; offset < count; offset++) {
            sendUpsertIfChanged(window.get((syncCursor + offset) % window.size()));
        }
        syncCursor = (syncCursor + count) % window.size();
    }

    private void sendUpsert(Entry entry) {
        var packetEntry = packetEntry(entry);
        sendUpsert(entry, packetEntry, packetEntry.hashCode());
    }

    private void sendUpsert(Entry entry, MentaloutRosterPackets.RosterEntry packetEntry, int fingerprint) {
        entry.lastFingerprint = fingerprint;
        MentaloutRosterPackets.sendUpsert(
                player,
                nextRevision(player.getUUID()),
                packetEntry,
                Math.round(currentStuporCost()),
                Math.round(currentImpressionCost())
        );
    }

    private void sendRemove(UUID targetUuid) {
        MentaloutRosterPackets.sendRemove(
                player,
                nextRevision(player.getUUID()),
                targetUuid,
                Math.round(currentStuporCost()),
                Math.round(currentImpressionCost())
        );
    }

    private void sendFull() {
        var roster = new ArrayList<MentaloutRosterPackets.RosterEntry>(entries.size());
        for (var entry : entries.values()) {
            var packetEntry = packetEntry(entry);
            entry.lastFingerprint = packetEntry.hashCode();
            roster.add(packetEntry);
        }
        MentaloutRosterPackets.sendFull(
                player,
                currentRevision(player.getUUID()),
                roster,
                Math.round(currentStuporCost()),
                Math.round(currentImpressionCost())
        );
    }

    private void sendFullUpdate() {
        nextRevision(player.getUUID());
        sendFull();
    }

    private MentaloutRosterPackets.RosterEntry packetEntry(Entry entry) {
        var subject = entry.subject;
        var flags = (byte) 0;
        if (entry.stupor != null && !entry.stupor.isClosed()) flags |= FLAG_STUPOR;
        if (entry.impression != null && !entry.impression.isClosed()) flags |= FLAG_IMPRESSION;
        if (entry.misidentification != null && !entry.misidentification.isClosed()) flags |= FLAG_MISIDENTIFICATION;
        if (isOverridden(entry)) flags |= FLAG_OVERRIDDEN;
        if (MentalControlRuntime
                .isProtectedTarget(subject)) flags |= FLAG_PROTECTED;
        if (subject instanceof ServerPlayer playerSubject
                && PlayerControlSessionManager.isResistant(playerSubject)) flags |= FLAG_RESISTANT;
        var remaining = entry.misidentification == null || entry.misidentification.isClosed()
                ? 0
                : Integer.MAX_VALUE;
        return new MentaloutRosterPackets.RosterEntry(
                subject.getUUID(),
                subject.getId(),
                BuiltInRegistries.ENTITY_TYPE.getKey(subject.getType()).toString(),
                subject.getDisplayName().getString(),
                quantize(subject.getHealth(), 0.25f),
                quantize(subject.getMaxHealth(), 0.25f),
                quantize(player.distanceTo(subject), 0.5f),
                supportLevel(subject),
                flags,
                remaining
        );
    }

    @Override
    protected void onUnregistered() {
        if (ended) return;
        ended = true;
        BY_CONTROLLER.remove(player.getUUID(), this);
        detachMisidentificationTarget();
        for (var entry : entries.values()) {
            entry.closeAll();
            var owners = BY_SUBJECT.get(entry.subject.getUUID());
            if (owners != null) {
                owners.remove(this);
                if (owners.isEmpty()) BY_SUBJECT.remove(entry.subject.getUUID());
            }
        }
        entries.clear();
        var system = AbilitySystemServer.getSystem(player);
        system.releaseMaintenanceOccupation(player.getUUID(), Skills.MENTAL_STUPOR.get().getKeyString());
        system.releaseMaintenanceOccupation(player.getUUID(), Skills.IMPRESSION_MANIPULATION.get().getKeyString());
        MentaloutRosterPackets.sendClear(player, nextRevision(player.getUUID()));
    }

    public enum ToggleResult {
        ADDED,
        REMOVED,
        INVALID,
        UNSUPPORTED,
        INSUFFICIENT_CP
    }

    public record BatchResult(int applied, int skipped, int failed, boolean active, boolean insufficientCp) {
        public static final BatchResult NONE = new BatchResult(0, 0, 0, false, false);
    }

    private static final class Entry {
        private final LivingEntity subject;
        private final float costWeight;
        private ControlHandle stupor;
        private ControlHandle impression;
        private ControlHandle misidentification;
        private long stuporStartedAt = Long.MAX_VALUE;
        private long impressionBuffUntil;
        private int lastFingerprint;

        private Entry(LivingEntity subject, float costWeight) {
            this.subject = subject;
            this.costWeight = costWeight;
        }

        private void closeAll() {
            close(stupor);
            close(impression);
            close(misidentification);
            stupor = null;
            impression = null;
            misidentification = null;
            stuporStartedAt = Long.MAX_VALUE;
            removeImpressionBuff();
        }

        private void applyImpressionBuff() {
            applyModifier(Attributes.ATTACK_SPEED, IMPRESSION_ATTACK_SPEED_ID);
            applyModifier(Attributes.MOVEMENT_SPEED, IMPRESSION_MOVEMENT_SPEED_ID);
        }

        private void applyModifier(
                net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                Identifier id
        ) {
            var instance = subject.getAttribute(attribute);
            if (instance == null || instance.getModifier(id) != null) return;
            instance.addTransientModifier(new AttributeModifier(
                    id, 0.10, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }

        private void removeImpressionBuff() {
            impressionBuffUntil = 0L;
            var attack = subject.getAttribute(Attributes.ATTACK_SPEED);
            if (attack != null) attack.removeModifier(IMPRESSION_ATTACK_SPEED_ID);
            var movement = subject.getAttribute(Attributes.MOVEMENT_SPEED);
            if (movement != null) movement.removeModifier(IMPRESSION_MOVEMENT_SPEED_ID);
        }
    }

    private static final class MentaloutTargetValidation {
        private MentaloutTargetValidation() {
        }

        private static boolean isValidRosterTarget(ServerPlayer player, LivingEntity subject) {
            if (player == null || subject == null || subject == player) return false;
            if (subject instanceof ServerPlayer targetPlayer) {
                if (!MentaloutConfig.allowPlayerRoster(player)
                        || targetPlayer.isSpectator()
                        || FriendlyFireSetting.shouldPrevent(player, targetPlayer)) {
                    return false;
                }
            }
            return subject.isAlive()
                    && !subject.isRemoved()
                    && subject.level() == player.level();
        }

        private static boolean isValidForcedTarget(ServerPlayer player, LivingEntity target) {
            return target != player
                    && target.isAlive()
                    && !target.isRemoved()
                    && target.level() == player.level()
                    && target.getBoundingBox().distanceToSqr(player.getEyePosition())
                    <= MentaloutTargetingRange.MAX_RANGE_SQR;
        }

        private static boolean isRetained(ServerPlayer player, LivingEntity subject) {
            return subject.isAlive()
                    && !subject.isRemoved()
                    && subject.level() == player.level()
                    && (!(subject instanceof ServerPlayer targetPlayer)
                    || MentaloutConfig.allowPlayerRoster(player) && !targetPlayer.isSpectator());
        }
    }

    private static final class MentaloutTargetingRange {
        private static final double MAX_RANGE_SQR = 16.0 * 16.0;

        private MentaloutTargetingRange() {
        }
    }
}
