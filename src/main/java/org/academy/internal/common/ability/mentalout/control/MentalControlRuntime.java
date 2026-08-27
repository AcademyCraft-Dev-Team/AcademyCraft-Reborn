package org.academy.internal.common.ability.mentalout.control;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.academy.AcademyCraft;
import org.academy.api.common.entitycontrol.*;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.mentalout.MentalControlMemory;
import org.academy.internal.common.ability.mentalout.MentalResistanceManager;
import org.academy.internal.common.ability.mentalout.MentaloutControlContext;
import org.academy.internal.common.world.damagesource.FriendlyFireSetting;
import org.jspecify.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public final class MentalControlRuntime {
    static final Identifier IMPRESSION_GUARD_SOURCE = AcademyCraft.academy("impression_guard_target");
    static final int IMPRESSION_GUARD_PRIORITY = Integer.MIN_VALUE;
    static final long IMPRESSION_GUARD_DURATION_TICKS = 100L;
    static final long RETALIATION_DURATION_TICKS = 100L;
    private static final Map<MinecraftServer, ServerState> SERVER_STATES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Object ADAPTER_LOCK = new Object();
    private static volatile List<AdapterRegistration> adapters = List.of();

    private MentalControlRuntime() {
    }

    public static ControlHandle apply(ControlRequest request) {
        Objects.requireNonNull(request, "request");
        var controller = request.controller();
        var subject = request.subject();
        var server = controller.level().getServer();
        if (server == null || subject.level().getServer() != server || controller.level() != subject.level()) {
            throw new IllegalArgumentException("Controller and subject must be in the same server level");
        }
        if (!controller.isAlive() || !subject.isAlive() || subject.isRemoved()) {
            throw new IllegalArgumentException("Controller and subject must be alive and loaded");
        }
        var now = subject.level().getGameTime();
        if (request.expiresAt() <= now) {
            throw new IllegalArgumentException("Control request must expire in the future");
        }

        var directives = indexDirectives(request.directives());
        var selections = new EnumMap<ControlCapability, AdapterSelection>(ControlCapability.class);
        for (var directive : request.directives()) {
            validateDirective(server, subject, directive);
            var selfMovementOrView = controller == subject
                    && (directive.capability() == ControlCapability.PATH_CONTROL
                    || directive.capability() == ControlCapability.VIEW_CONTROL);
            var resolution = resolve(subject, directive.capability(), selfMovementOrView);
            if (!resolution.evaluation().supported() || resolution.registration() == null) {
                if (resolution.evaluation().reason() == ControlRejectionReason.PROTECTED_PLAYER
                        || resolution.evaluation().reason() == ControlRejectionReason.IMMUNE_TAG) {
                    notifyProtectionBlocked(controller, subject);
                }
                throw new ControlApplyException(
                        resolution.evaluation().reason(),
                        directive.capability(),
                        "Mental control rejected for " + subject.getType() + ": "
                                + resolution.evaluation().reason()
                );
            }
            selections.put(
                    directive.capability(),
                    new AdapterSelection(
                            resolution.registration().id(),
                            resolution.registration().priority(),
                            resolution.evaluation().support(),
                            resolution.registration().adapter()
                    )
            );
        }

        var state = state(server);
        UUID leaseId;
        synchronized (state) {
            UUID guardianRelationLeaseId = null;
            if (request.source().equals(IMPRESSION_GUARD_SOURCE)) {
                var relation = state.leases.effective(
                        subject.getUUID(),
                        ControlCapability.RELATION_CONTROL,
                        now
                );
                if (relation == null || !relation.controllerId().equals(controller.getUUID())
                        || !(relation.directive() instanceof ControlDirective.ImpressionAlliance)) {
                    throw new ControlApplyException(
                            ControlRejectionReason.INVALID_DIRECTIVE,
                            ControlCapability.FORCE_TARGET,
                            "Impression guard requires the controller's effective alliance lease"
                    );
                }
                guardianRelationLeaseId = relation.leaseId();
            }
            var input = new LeaseInput(
                    controller.getUUID(),
                    subject.getUUID(),
                    request.source(),
                    request.priority(),
                    request.expiresAt(),
                    controller.level().dimension().identifier(),
                    subject.level().dimension().identifier(),
                    directives,
                    selections,
                    guardianRelationLeaseId
            );
            var snapshot = state.leases.snapshotState();
            leaseId = state.leases.add(input);
            var invalidGuards = state.leases.removeInvalidImpressionGuards(
                    Set.of(subject.getUUID()),
                    now
            );
            try {
                reconcileSubject(server, state, subject.getUUID());
            } catch (BindingFailure failure) {
                closeBindingsForSubject(state, subject.getUUID());
                state.leases.restore(snapshot);
                var restoration = new RemovalResult();
                restoration.addSubject(subject.getUUID());
                reconcileRecovering(server, state, restoration);
                throw new ControlApplyException(
                        ControlRejectionReason.ADAPTER_ERROR,
                        failure.capability(),
                        "Mental control adapter failed to activate",
                        failure.getCause()
                );
            }
            reconcileTargets(server, state, invalidGuards);
        }
        if (subject instanceof Mob mob && directives.containsKey(ControlDomain.TARGET)) {
            maintainTarget(mob);
        }
        if (subject instanceof Mob mob && directives.containsKey(ControlDomain.RELATION)) {
            enforceTargetWhitelist(mob);
        }
        MentalControlMemory.remember(controller, subject);
        var handle = new RuntimeHandle(server, leaseId);
        synchronized (state) {
            state.handles.put(leaseId, new WeakReference<>(handle));
        }
        return handle;
    }

    public static void registerAdapter(Identifier id, int priority, MentalControlAdapter adapter) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(adapter, "adapter");
        synchronized (ADAPTER_LOCK) {
            var next = new ArrayList<>(adapters);
            next.removeIf(registration -> registration.id().equals(id));
            next.add(new AdapterRegistration(id, priority, adapter));
            adapters = List.copyOf(next);
        }
    }

    public static Optional<MentalControlAdapter> findAdapter(LivingEntity subject) {
        Objects.requireNonNull(subject, "subject");
        for (var capability : ControlCapability.values()) {
            var resolution = resolve(subject, capability);
            if (resolution.evaluation().supported() && resolution.registration() != null) {
                return Optional.of(resolution.registration().adapter());
            }
        }
        return Optional.empty();
    }

    public static Optional<MentalControlAdapter> findAdapter(
            LivingEntity subject,
            ControlCapability capability
    ) {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(capability, "capability");
        var resolution = resolve(subject, capability);
        return resolution.evaluation().supported() && resolution.registration() != null
                ? Optional.of(resolution.registration().adapter())
                : Optional.empty();
    }

    public static ControlEvaluation evaluate(LivingEntity subject, ControlCapability capability) {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(capability, "capability");
        return resolve(subject, capability).evaluation();
    }

    public static Optional<ControlInspection> inspect(
            LivingEntity subject,
            ControlCapability capability
    ) {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(capability, "capability");
        var state = stateIfPresent(subject.level().getServer());
        if (state == null) return Optional.empty();
        var effective = state.leases.effective(
                subject.getUUID(),
                capability,
                subject.level().getGameTime()
        );
        if (effective == null) return Optional.empty();
        return Optional.of(new ControlInspection(
                capability,
                effective.leaseId(),
                effective.controllerId(),
                effective.source(),
                effective.priority(),
                effective.expiresAt(),
                effective.directive()
        ));
    }

    public static Optional<ControlDirective> effectiveDirective(
            LivingEntity subject,
            ControlCapability capability
    ) {
        return inspect(subject, capability).map(ControlInspection::directive);
    }


    public static boolean hasActiveControl(LivingEntity subject) {
        Objects.requireNonNull(subject, "subject");
        var state = stateIfPresent(subject.level().getServer());
        if (state != null) {
            var now = subject.level().getGameTime();
            for (var capability : ControlCapability.values()) {
                if (state.leases.effective(subject.getUUID(), capability, now) != null) return true;
            }
        }
        return MentalPerceptionRuntime.isAffected(subject);
    }

    public static boolean isFrozen(Mob mob) {
        return isFrozen((LivingEntity) mob);
    }

    public static boolean isFrozen(LivingEntity subject) {
        var state = stateIfPresent(subject.level().getServer());
        return state != null && state.leases.isFrozen(subject.getUUID(), subject.level().getGameTime());
    }

    /**
     * Returns whether vanilla target acquisition must yield to an explicit mental-control policy.
     * Relation control keeps the normal goal selector available for permitted retaliation; its
     * special Brain targeting is filtered separately by {@link #suppressesAutonomousBrain(Mob)}.
     */
    public static boolean suppressesAutonomousTargeting(Mob subject) {
        Objects.requireNonNull(subject, "subject");
        var state = stateIfPresent(subject.level().getServer());
        if (state == null) return false;
        var now = subject.level().getGameTime();
        return state.leases.effective(subject.getUUID(), ControlCapability.FORCE_TARGET, now) != null
                || state.leases.effective(subject.getUUID(), ControlCapability.PATH_CONTROL, now) != null
                || state.leases.effective(subject.getUUID(), ControlCapability.DIRECT_CONTROL, now) != null
                || state.leases.effective(subject.getUUID(), ControlCapability.GUARD_CONTROL, now) != null
                || state.leases.effective(subject.getUUID(), ControlCapability.FREEZE_AI, now) != null;
    }

    /**
     * Returns whether a movement command owns the mob's complete action loop. While this is true,
     * vanilla goals and entity-specific server AI must not tick; navigation and movement controls
     * are still ticked by {@code Mob} so the active control binding can drive them continuously.
     */
    public static boolean suppressesAutonomousActions(Mob subject) {
        Objects.requireNonNull(subject, "subject");
        var state = stateIfPresent(subject.level().getServer());
        if (state == null) return false;
        var now = subject.level().getGameTime();
        return state.leases.effective(
                subject.getUUID(), ControlCapability.PATH_CONTROL, now) != null
                || state.leases.effective(
                subject.getUUID(), ControlCapability.DIRECT_CONTROL, now) != null;
    }

    public static boolean suppressesAutonomousBrain(Mob subject) {
        var state = stateIfPresent(subject.level().getServer());
        if (state == null) return false;
        var now = subject.level().getGameTime();
        var hasExclusivePolicy = suppressesAutonomousTargeting(subject);
        if (!hasExclusivePolicy) return false;
        var explicitTarget = getForcedTarget(subject);
        if (explicitTarget == null) explicitTarget = getGuardTarget(subject);
        if (explicitTarget != null) return false;
        var brainTarget = subject.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET);
        return brainTarget == null || brainTarget.isEmpty()
                || attackDecision(subject, brainTarget.get()) == AttackDecision.DENY;
    }

    public static boolean suppressesAutonomousCombat(Mob subject) {
        if (suppressesAutonomousTargeting(subject)) return true;
        var state = stateIfPresent(subject.level().getServer());
        if (state == null) return false;
        var relation = state.leases.effective(
                subject.getUUID(),
                ControlCapability.RELATION_CONTROL,
                subject.level().getGameTime()
        );
        if (relation == null
                || !(relation.directive() instanceof ControlDirective.ImpressionAlliance)) return false;
        var target = getRawTarget(subject);
        if (target == null) {
            var memory = subject.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET);
            if (memory != null && memory.isPresent()) target = memory.get();
        }
        return target == null || attackDecision(subject, target) == AttackDecision.DENY;
    }

    public static @Nullable LivingEntity getForcedTarget(Mob mob) {
        return getForcedTarget((LivingEntity) mob);
    }

    public static @Nullable LivingEntity getForcedTarget(LivingEntity subject) {
        var server = subject.level().getServer();
        var state = stateIfPresent(server);
        if (server == null || state == null || !(subject.level() instanceof ServerLevel level)) return null;
        var targetId = state.leases.forcedTarget(subject.getUUID(), subject.level().getGameTime());
        if (targetId == null) return null;
        var target = level.getEntity(targetId);
        return target instanceof LivingEntity living && living.isAlive() && !living.isRemoved() ? living : null;
    }

    public static @Nullable LivingEntity getGuardTarget(LivingEntity subject) {
        Objects.requireNonNull(subject, "subject");
        var server = subject.level().getServer();
        var state = stateIfPresent(server);
        if (server == null || state == null) return null;
        synchronized (state) {
            var targetId = state.guardTargets.get(subject.getUUID());
            if (targetId == null) return null;
            var target = findLivingEntity(server, targetId);
            if (target == null || target.level() != subject.level() || !target.isAlive() || target.isRemoved()) {
                state.guardTargets.remove(subject.getUUID(), targetId);
                return null;
            }
            return target;
        }
    }

    static void updateGuardTarget(Mob subject, @Nullable LivingEntity target) {
        Objects.requireNonNull(subject, "subject");
        var state = stateIfPresent(subject.level().getServer());
        if (state == null) return;
        synchronized (state) {
            if (target == null) {
                state.guardTargets.remove(subject.getUUID());
            } else {
                state.guardTargets.put(subject.getUUID(), target.getUUID());
            }
        }
    }

    public static boolean canForceAttack(LivingEntity attacker, LivingEntity target) {
        if (attacker.level() != target.level() || !target.isAlive() || target.isRemoved()) return false;
        var state = stateIfPresent(attacker.level().getServer());
        if (state == null) return false;
        var targetId = state.leases.forcedTarget(attacker.getUUID(), attacker.level().getGameTime());
        return target.getUUID().equals(targetId);
    }

    public static AttackDecision attackDecision(LivingEntity attacker, LivingEntity target) {
        Objects.requireNonNull(attacker, "attacker");
        Objects.requireNonNull(target, "target");
        if (attacker.level() != target.level() || !target.isAlive() || target.isRemoved()) {
            return AttackDecision.PASS;
        }
        var state = stateIfPresent(attacker.level().getServer());
        if (state == null) {
            return MentalPerceptionRuntime.decision(attacker, target)
                    == PerceptionDecision.HIDDEN
                    ? AttackDecision.DENY
                    : AttackDecision.PASS;
        }
        var now = attacker.level().getGameTime();
        var guard = state.leases.effective(attacker.getUUID(), ControlCapability.GUARD_CONTROL, now);
        if (guard != null && guard.directive() instanceof ControlDirective.Guard guardDirective) {
            UUID selectedGuardTarget;
            synchronized (state) {
                selectedGuardTarget = state.guardTargets.get(attacker.getUUID());
            }
            var controller = attacker.level().getServer().getPlayerList().getPlayer(guard.controllerId());
            var protectsDestination = guardDirective.destination() instanceof ControlDestination.Entity(var uuid)
                    && uuid.equals(target.getUUID());
            var protectsController = controller != null && (target == controller
                    || controller.isAlliedTo(target)
                    || FriendlyFireSetting.shouldPrevent(controller, target));
            if (protectsDestination || protectsController) return AttackDecision.DENY;
            return target.getUUID().equals(selectedGuardTarget)
                    ? AttackDecision.ALLOW
                    : AttackDecision.DENY;
        }
        var controlledDecision = controlledAttackDecision(
                state.leases,
                state.targetWhitelist,
                attacker.getUUID(),
                target.getUUID(),
                now
        );
        if (controlledDecision != AttackDecision.PASS) return controlledDecision;
        if (MentalPerceptionRuntime.decision(attacker, target)
                == PerceptionDecision.HIDDEN) {
            return AttackDecision.DENY;
        }

        return allianceDecision(attacker, target);
    }

    /**
     * Damage and autonomous hostility deliberately use different policies. A controller may hurt
     * a roster subject when friendly fire is enabled, even though the same alliance relation keeps
     * the subject from treating its controller as an AI target.
     */
    public static AttackDecision damageDecision(LivingEntity attacker, LivingEntity target) {
        var decision = attackDecision(attacker, target);
        if (!(attacker instanceof ServerPlayer controller)
                || !isControlledBy(controller, target)) return decision;
        if (!FriendlyFireSetting.isFriendlyFireEnabled(controller)) return AttackDecision.DENY;
        return allianceDecision(attacker, target) == AttackDecision.DENY
                ? AttackDecision.PASS
                : decision;
    }

    private static boolean isControlledBy(ServerPlayer controller, LivingEntity target) {
        var context = MentaloutControlContext.get(controller);
        if (context != null && context.contains(target.getUUID())) return true;
        var state = stateIfPresent(controller.level().getServer());
        return state != null && state.leases.hasActiveControl(
                controller.getUUID(), target.getUUID(), target.level().getGameTime());
    }

    public static AttackDecision allianceDecision(LivingEntity attacker, LivingEntity target) {
        Objects.requireNonNull(attacker, "attacker");
        Objects.requireNonNull(target, "target");
        if (attacker.level() != target.level() || !target.isAlive() || target.isRemoved()) {
            return AttackDecision.PASS;
        }
        var state = stateIfPresent(attacker.level().getServer());
        if (state == null) return AttackDecision.PASS;
        var now = attacker.level().getGameTime();
        return relationDecision(
                state.leases,
                state.targetWhitelist,
                attacker.getUUID(),
                target.getUUID(),
                now
        );
    }

    static AttackDecision relationDecision(
            LeaseTable leases,
            TargetWhitelist targetWhitelist,
            UUID attackerId,
            UUID targetId,
            long now
    ) {
        var forcedTarget = leases.forcedTarget(attackerId, now);
        if (targetId.equals(forcedTarget)) return AttackDecision.ALLOW;
        var attackerRelation = leases.effective(
                attackerId,
                ControlCapability.RELATION_CONTROL,
                now
        );
        if (attackerRelation != null
                && attackerRelation.directive() instanceof ControlDirective.ImpressionAlliance
                && targetWhitelist.allows(
                attackerId,
                targetId,
                attackerRelation.leaseId(),
                now
        )) {
            return AttackDecision.ALLOW;
        }

        var targetRelation = leases.effective(targetId, ControlCapability.RELATION_CONTROL, now);
        var attackerHasRelation = attackerRelation != null
                && attackerRelation.directive() instanceof ControlDirective.ImpressionAlliance;
        var targetHasRelation = targetRelation != null
                && targetRelation.directive() instanceof ControlDirective.ImpressionAlliance;
        if (attackerHasRelation) {
            if (targetId.equals(attackerRelation.controllerId())) return AttackDecision.DENY;
            if (targetHasRelation
                    && attackerRelation.controllerId().equals(targetRelation.controllerId())) {
                return AttackDecision.DENY;
            }
        }
        if (targetHasRelation) {
            if (attackerId.equals(targetRelation.controllerId())) return AttackDecision.DENY;
            if (leases.hasImpressionAlliance(
                    targetRelation.controllerId(),
                    attackerId,
                    now
            )) {
                return AttackDecision.DENY;
            }
        }
        return AttackDecision.PASS;
    }

    public static boolean isHostilityAllowed(LivingEntity subject, @Nullable Entity target) {
        Objects.requireNonNull(subject, "subject");
        if (target instanceof LivingEntity livingTarget) {
            return attackDecision(subject, livingTarget) != AttackDecision.DENY;
        }
        var state = stateIfPresent(subject.level().getServer());
        if (state == null) return true;
        if (subject instanceof Mob mob && suppressesAutonomousTargeting(mob)) return false;
        var relation = state.leases.effective(
                subject.getUUID(),
                ControlCapability.RELATION_CONTROL,
                subject.level().getGameTime()
        );
        return relation == null
                || !(relation.directive() instanceof ControlDirective.ImpressionAlliance);
    }

    static AttackDecision controlledAttackDecision(
            LeaseTable leases,
            TargetWhitelist targetWhitelist,
            UUID attackerId,
            UUID targetId,
            long now
    ) {
        var forcedTarget = leases.forcedTarget(attackerId, now);
        if (forcedTarget != null) {
            return targetId.equals(forcedTarget) ? AttackDecision.ALLOW : AttackDecision.DENY;
        }
        if (leases.effective(attackerId, ControlCapability.PATH_CONTROL, now) != null
                || leases.effective(attackerId, ControlCapability.FREEZE_AI, now) != null) {
            return AttackDecision.DENY;
        }
        var relation = leases.effective(attackerId, ControlCapability.RELATION_CONTROL, now);
        if (relation == null
                || !(relation.directive() instanceof ControlDirective.ImpressionAlliance)) {
            return AttackDecision.PASS;
        }
        return targetWhitelist.allows(
                attackerId,
                targetId,
                relation.leaseId(),
                now
        ) ? AttackDecision.ALLOW : AttackDecision.DENY;
    }

    public static boolean authorizeRetaliation(LivingEntity subject, LivingEntity aggressor) {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(aggressor, "aggressor");
        if (subject == aggressor || subject.level() != aggressor.level()
                || !subject.isAlive() || subject.isRemoved()
                || !aggressor.isAlive() || aggressor.isRemoved()
                || MentalControlProtection.rejectionReason(subject) != null) return false;
        var state = stateIfPresent(subject.level().getServer());
        if (state == null) return false;
        var now = subject.level().getGameTime();
        var relation = state.leases.effective(
                subject.getUUID(),
                ControlCapability.RELATION_CONTROL,
                now
        );
        if (relation == null
                || !(relation.directive() instanceof ControlDirective.ImpressionAlliance)) return false;
        var expiresAt = Math.min(relation.expiresAt(), now + RETALIATION_DURATION_TICKS);
        if (expiresAt <= now) return false;
        state.targetWhitelist.authorize(
                subject.getUUID(),
                aggressor.getUUID(),
                relation.leaseId(),
                expiresAt
        );
        return true;
    }

    public static void alertImpressionAllies(ServerPlayer controller, LivingEntity aggressor) {
        Objects.requireNonNull(controller, "controller");
        Objects.requireNonNull(aggressor, "aggressor");
        if (controller == aggressor || controller.level() != aggressor.level()
                || !controller.isAlive() || !aggressor.isAlive() || aggressor.isRemoved()) return;
        var server = controller.level().getServer();
        var state = stateIfPresent(server);
        if (server == null || state == null) return;

        var now = controller.level().getGameTime();
        List<ImpressionSubject> candidates;
        synchronized (state) {
            candidates = state.leases.impressionSubjects(controller.getUUID(), now);
        }
        for (var candidate : candidates) {
            var subject = findLivingEntity(server, candidate.subjectId());
            if (subject == null || subject == aggressor || subject.level() != controller.level()
                    || !subject.isAlive() || subject.isRemoved()) continue;
            synchronized (state) {
                if (!canAssignGuardianTarget(
                        state.leases,
                        controller.getUUID(),
                        subject.getUUID(),
                        now
                )) continue;
            }
            if (!evaluate(subject, ControlCapability.FORCE_TARGET).supported()) continue;

            var expiresAt = Math.min(
                    candidate.expiresAt(),
                    now + IMPRESSION_GUARD_DURATION_TICKS
            );
            if (expiresAt <= now) continue;
            try {
                apply(new ControlRequest(
                        controller,
                        subject,
                        IMPRESSION_GUARD_SOURCE,
                        IMPRESSION_GUARD_PRIORITY,
                        expiresAt,
                        List.of(new ControlDirective.ForceTarget(aggressor.getUUID()))
                ));
            } catch (RuntimeException exception) {
                AcademyCraft.LOGGER.debug(
                        "Impression guard could not redirect {} toward {}",
                        subject.getUUID(),
                        aggressor.getUUID(),
                        exception
                );
            }
        }
    }

    static boolean canAssignGuardianTarget(
            LeaseTable leases,
            UUID controllerId,
            UUID subjectId,
            long now
    ) {
        var relation = leases.effective(subjectId, ControlCapability.RELATION_CONTROL, now);
        if (relation == null || !relation.controllerId().equals(controllerId)
                || !(relation.directive() instanceof ControlDirective.ImpressionAlliance)) return false;
        var target = leases.effective(subjectId, ControlCapability.FORCE_TARGET, now);
        return target == null || target.source().equals(IMPRESSION_GUARD_SOURCE);
    }

    public static boolean isBossCost(LivingEntity subject) {
        return MentalControlProtection.isBossCost(subject);
    }

    public static boolean isProtectedTarget(LivingEntity subject) {
        return subject == null || MentalControlProtection.rejectionReason(subject) != null;
    }

    public static void notifyProtectionBlocked(ServerPlayer controller, LivingEntity subject) {
        MentalControlProtection.notifyBlocked(controller, subject);
    }

    public static void maintainTarget(Mob mob) {
        var target = getForcedTarget(mob);
        if (target == null) return;
        if (getRawTarget(mob) != target) mob.setTarget(target);
        mob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
    }

    public static void enforceTargetWhitelist(Mob mob) {
        var state = stateIfPresent(mob.level().getServer());
        if (state == null) return;
        var now = mob.level().getGameTime();
        var forcedTarget = getForcedTarget(mob);
        var guardTarget = getGuardTarget(mob);
        var commandedTarget = forcedTarget != null ? forcedTarget : guardTarget;
        if (suppressesAutonomousTargeting(mob)) {
            enforceAngerWhitelist(mob);
            if (commandedTarget != null) {
                if (getRawTarget(mob) != commandedTarget) mob.setTarget(commandedTarget);
                mob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, commandedTarget);
            } else {
                if (getRawTarget(mob) != null) mob.setTarget(null);
                mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
                mob.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
            }
        }
        var relation = state.leases.effective(
                mob.getUUID(),
                ControlCapability.RELATION_CONTROL,
                now
        );
        if (relation == null
                || !(relation.directive() instanceof ControlDirective.ImpressionAlliance)) return;
        enforceAngerWhitelist(mob);
        if (forcedTarget != null) {
            maintainTarget(mob);
            return;
        }

        var rawTarget = getRawTarget(mob);
        if (rawTarget != null && attackDecision(mob, rawTarget) == AttackDecision.DENY) {
            mob.setTarget(null);
        }
        var brainTarget = mob.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET);
        var clearedCombatTarget = false;
        if (brainTarget != null && brainTarget.isPresent()
                && attackDecision(mob, brainTarget.get()) == AttackDecision.DENY) {
            mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            clearedCombatTarget = true;
        }
        clearedCombatTarget |= clearDeniedBrainTarget(mob, MemoryModuleType.NEAREST_ATTACKABLE);
        clearedCombatTarget |= clearDeniedBrainTarget(mob, MemoryModuleType.ROAR_TARGET);
        clearedCombatTarget |= clearDeniedBrainTarget(
                mob, MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER);
        clearedCombatTarget |= clearDeniedBrainTarget(
                mob, MemoryModuleType.NEAREST_VISIBLE_NEMESIS);
        if (clearedCombatTarget) {
            mob.setAggressive(false);
            var brain = mob.getBrain();
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
            brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
            if (!suppressesAutonomousActions(mob)) mob.getNavigation().stop();
        }
    }

    private static <T extends LivingEntity> boolean clearDeniedBrainTarget(
            Mob mob,
            MemoryModuleType<T> memoryType
    ) {
        var memory = mob.getBrain().getMemoryInternal(memoryType);
        if (memory == null || memory.isEmpty()
                || attackDecision(mob, memory.get()) != AttackDecision.DENY) return false;
        mob.getBrain().eraseMemory(memoryType);
        return true;
    }

    private static void enforceAngerWhitelist(Mob mob) {
        if (!(mob.level() instanceof ServerLevel level)) return;

        if (mob instanceof NeutralMob neutral) {
            var angerReference = neutral.getPersistentAngerTarget();
            var angerTarget = EntityReference.getLivingEntity(angerReference, level);
            if (angerReference == null || angerTarget == null
                    || attackDecision(mob, angerTarget) == AttackDecision.DENY) {
                neutral.setPersistentAngerTarget(null);
                neutral.setPersistentAngerEndTime(NeutralMob.NO_ANGER_END_TIME);
            }
        }

        var brain = mob.getBrain();
        var angryAt = brain.getMemoryInternal(MemoryModuleType.ANGRY_AT);
        if (angryAt != null && angryAt.isPresent()) {
            var entity = level.getEntity(angryAt.get());
            if (!(entity instanceof LivingEntity angerTarget)
                    || attackDecision(mob, angerTarget) == AttackDecision.DENY) {
                brain.eraseMemory(MemoryModuleType.ANGRY_AT);
            }
        }
        brain.eraseMemory(MemoryModuleType.UNIVERSAL_ANGER);
    }

    public static void tick(MinecraftServer server) {
        var state = stateIfPresent(server);
        if (state == null) return;

        synchronized (state) {
            var now = server.overworld().getGameTime();
            // ServerTickEvent.Pre runs immediately before levels advance their game time.
            var effectiveNow = now + 1L;
            var removal = state.leases.expire(effectiveNow);
            state.targetWhitelist.expire(effectiveNow);
            var invalidLeaseIds = new HashSet<UUID>();
            var unavailableTargetLeaseIds = new HashSet<UUID>();
            for (var lease : state.leases.snapshot()) {
                var controller = server.getPlayerList().getPlayer(lease.controllerId());
                var subject = findLivingEntity(server, lease.subjectId());
                if (controller == null || !controller.isAlive()
                        || !controller.level().dimension().identifier().equals(lease.controllerDimension())
                        || subject == null || !subject.isAlive() || subject.isRemoved()
                        || !subject.level().dimension().identifier().equals(lease.subjectDimension())
                        || MentalControlProtection.rejectionReason(subject) != null
                        || lease.source().equals(IMPRESSION_GUARD_SOURCE)
                        && !state.leases.hasImpressionAlliance(
                        lease.controllerId(),
                        lease.subjectId(),
                        lease.guardianRelationLeaseId(),
                        effectiveNow
                )) {
                    invalidLeaseIds.add(lease.id());
                    continue;
                }
                for (var targetId : lease.referencedTargets()) {
                    var target = findLivingEntity(server, targetId);
                    if (target == null || !target.isAlive() || target.isRemoved() || target.level() != subject.level()) {
                        invalidLeaseIds.add(lease.id());
                        unavailableTargetLeaseIds.add(lease.id());
                        break;
                    }
                }
                if (!invalidLeaseIds.contains(lease.id()) && subject instanceof ServerPlayer playerSubject) {
                    MentalResistanceManager.markAffected(
                            controller,
                            playerSubject,
                            lease.source().equals(Skills.MENTAL_TAKEOVER.get().getKey())
                    );
                }
            }
            recordFailures(state, unavailableTargetLeaseIds, ControlFailureReason.TARGET_UNAVAILABLE);
            removal.merge(state.leases.removeAll(invalidLeaseIds));
            reconcileRecovering(server, state, removal);

            var failedBindings = new HashSet<UUID>();
            for (var active : List.copyOf(state.activeBindings.values())) {
                try {
                    active.binding().tick();
                } catch (Throwable throwable) {
                    AcademyCraft.LOGGER.error(
                            "Mental control binding {} failed during tick",
                            active.leaseId(),
                            throwable
                    );
                    failedBindings.add(active.leaseId());
                }
            }
            if (!failedBindings.isEmpty()) {
                recordFailures(state, failedBindings, ControlFailureReason.ADAPTER_ERROR);
                var failures = state.leases.removeAll(failedBindings);
                reconcileRecovering(server, state, failures);
            }

            var completionStates = new HashMap<UUID, Boolean>();
            var completionFailures = new HashSet<UUID>();
            for (var active : List.copyOf(state.activeBindings.values())) {
                try {
                    completionStates.merge(
                            active.leaseId(),
                            active.binding().isComplete(),
                            Boolean::logicalAnd
                    );
                } catch (Throwable throwable) {
                    AcademyCraft.LOGGER.error(
                            "Mental control binding {} failed during completion check",
                            active.leaseId(),
                            throwable
                    );
                    completionFailures.add(active.leaseId());
                }
            }
            if (!completionFailures.isEmpty()) {
                recordFailures(state, completionFailures, ControlFailureReason.ADAPTER_ERROR);
                var failures = state.leases.removeAll(completionFailures);
                reconcileRecovering(server, state, failures);
            }
            var completedLeases = completionStates.entrySet().stream()
                    .filter(Map.Entry::getValue)
                    .map(Map.Entry::getKey)
                    .filter(leaseId -> !completionFailures.contains(leaseId))
                    .collect(Collectors.toUnmodifiableSet());
            if (!completedLeases.isEmpty()) {
                for (var active : state.activeBindings.values()) {
                    if (!completedLeases.contains(active.leaseId())) continue;
                    active.binding().failureReason().ifPresent(reason ->
                            recordFailure(state, active.leaseId(), reason));
                }
                var completed = state.leases.removeAll(completedLeases);
                reconcileRecovering(server, state, completed);
            }

            for (var subjectId : state.leases.subjectIds()) {
                if (findLivingEntity(server, subjectId) instanceof Mob mob) {
                    enforceTargetWhitelist(mob);
                }
            }
        }
        removeEmptyState(server, state);
    }

    public static void beforeNavigationTick(Mob mob) {
        runMovementHook(mob, false);
    }

    public static void beforeMoveControlTick(Mob mob) {
        runMovementHook(mob, true);
    }

    public static void beforeLookControlTick(LivingEntity subject) {
        Objects.requireNonNull(subject, "subject");
        var server = subject.level().getServer();
        var state = stateIfPresent(server);
        if (server == null || state == null) return;
        var failedLeases = new HashSet<UUID>();
        synchronized (state) {
            for (var entry : List.copyOf(state.activeBindings.entrySet())) {
                if (!entry.getKey().subjectId().equals(subject.getUUID())
                        || !entry.getValue().capability().domains().contains(ControlDomain.VIEW)) continue;
                try {
                    entry.getValue().binding().beforeLookControlTick();
                } catch (Throwable throwable) {
                    AcademyCraft.LOGGER.error(
                            "Mental control binding {} failed before look control tick",
                            entry.getValue().leaseId(),
                            throwable
                    );
                    failedLeases.add(entry.getValue().leaseId());
                }
            }
            if (!failedLeases.isEmpty()) {
                recordFailures(state, failedLeases, ControlFailureReason.ADAPTER_ERROR);
                var failures = state.leases.removeAll(failedLeases);
                reconcileRecovering(server, state, failures);
            }
        }
        removeEmptyState(server, state);
    }

    private static void runMovementHook(Mob mob, boolean moveControlHook) {
        Objects.requireNonNull(mob, "mob");
        var server = mob.level().getServer();
        var state = stateIfPresent(server);
        if (server == null || state == null) return;
        var failedLeases = new HashSet<UUID>();
        synchronized (state) {
            for (var entry : List.copyOf(state.activeBindings.entrySet())) {
                if (!entry.getKey().subjectId().equals(mob.getUUID())
                        || !entry.getValue().capability().domains().contains(ControlDomain.MOVEMENT)) {
                    continue;
                }
                try {
                    if (moveControlHook) {
                        entry.getValue().binding().beforeMoveControlTick();
                    } else {
                        entry.getValue().binding().beforeNavigationTick();
                    }
                } catch (Throwable throwable) {
                    AcademyCraft.LOGGER.error(
                            "Mental control binding {} failed before {} tick",
                            entry.getValue().leaseId(),
                            moveControlHook ? "move control" : "navigation",
                            throwable
                    );
                    failedLeases.add(entry.getValue().leaseId());
                }
            }
            if (!failedLeases.isEmpty()) {
                recordFailures(state, failedLeases, ControlFailureReason.ADAPTER_ERROR);
                var failures = state.leases.removeAll(failedLeases);
                reconcileRecovering(server, state, failures);
            }
        }
        removeEmptyState(server, state);
    }

    public static void releaseByController(UUID controllerId) {
        Objects.requireNonNull(controllerId, "controllerId");
        for (var entry : serverStateSnapshot()) releaseByController(entry.getKey(), controllerId);
    }

    public static void releaseByController(MinecraftServer server, UUID controllerId) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(controllerId, "controllerId");
        var state = stateIfPresent(server);
        if (state == null) return;
        synchronized (state) {
            state.targetWhitelist.releaseEntity(controllerId);
            var removal = state.leases.removeByController(controllerId);
            reconcileRecovering(server, state, removal);
        }
        removeEmptyState(server, state);
    }

    public static void releaseBySubject(UUID subjectId) {
        Objects.requireNonNull(subjectId, "subjectId");
        for (var entry : serverStateSnapshot()) releaseBySubject(entry.getKey(), subjectId);
    }

    public static void releaseBySubject(MinecraftServer server, UUID subjectId) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(subjectId, "subjectId");
        var state = stateIfPresent(server);
        if (state == null) return;
        synchronized (state) {
            state.targetWhitelist.releaseEntity(subjectId);
            state.guardTargets.remove(subjectId);
            state.guardTargets.entrySet().removeIf(entry -> entry.getValue().equals(subjectId));
            var removal = state.leases.removeBySubject(subjectId);
            reconcileRecovering(server, state, removal);
        }
        removeEmptyState(server, state);
    }


    public static void releaseByControllerSourceAndSubject(
            MinecraftServer server,
            UUID controllerId,
            Identifier source,
            UUID subjectId
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(controllerId, "controllerId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(subjectId, "subjectId");
        var state = stateIfPresent(server);
        if (state == null) return;
        synchronized (state) {
            var removal = state.leases.removeByControllerSourceSubject(controllerId, source, subjectId);
            reconcileRecovering(server, state, removal);
        }
        removeEmptyState(server, state);
    }

    public static void clear(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        ServerState state;
        synchronized (SERVER_STATES) {
            state = SERVER_STATES.remove(server);
        }
        if (state == null) return;
        synchronized (state) {
            var removal = state.leases.clear();
            closeAllBindings(state);
            reconcileTargets(server, state, removal);
        }
    }

    static @Nullable LivingEntity findLivingEntity(MinecraftServer server, UUID entityId) {
        for (var level : server.getAllLevels()) {
            var entity = level.getEntity(entityId);
            if (entity instanceof LivingEntity living) return living;
        }
        return null;
    }

    private static Resolution resolve(LivingEntity subject, ControlCapability capability) {
        return resolve(subject, capability, false);
    }

    private static Resolution resolve(
            LivingEntity subject,
            ControlCapability capability,
            boolean ignoreProtection
    ) {
        var protection = ignoreProtection ? null : MentalControlProtection.rejectionReason(subject);
        if (protection != null) {
            return Resolution.rejected(capability, protection);
        }

        var matching = new ArrayList<AdapterRegistration>();
        var failures = new ArrayList<AdapterRegistration>();
        for (var registration : adapters) {
            try {
                if (registration.adapter().matches(subject)) matching.add(registration);
            } catch (Throwable throwable) {
                failures.add(registration);
            }
        }

        if (matching.isEmpty() && failures.isEmpty()) {
            return Resolution.rejected(capability, ControlRejectionReason.NO_ADAPTER);
        }

        var candidates = new ArrayList<CapabilityCandidate>();
        for (var registration : matching) {
            try {
                var support = Objects.requireNonNull(
                        registration.adapter().support(subject, capability),
                        "Adapter support must not be null"
                );
                var reason = ControlRejectionReason.SUPPORTED;
                if (!support.isSupported()) {
                    reason = Objects.requireNonNull(
                            registration.adapter().rejectionReason(subject, capability),
                            "Adapter rejection reason must not be null"
                    );
                    if (reason == ControlRejectionReason.SUPPORTED) {
                        reason = ControlRejectionReason.UNSUPPORTED_CAPABILITY;
                    }
                }
                candidates.add(new CapabilityCandidate(registration, support, reason));
            } catch (Throwable throwable) {
                failures.add(registration);
            }
        }

        return selectAdapter(capability, candidates, failures);
    }

    static Resolution selectAdapter(
            ControlCapability capability,
            List<CapabilityCandidate> candidates,
            List<AdapterRegistration> failures
    ) {
        var supported = candidates.stream()
                .filter(candidate -> candidate.support().isSupported())
                .toList();
        var highestSupportedPriority = supported.stream()
                .mapToInt(candidate -> candidate.registration().priority())
                .max()
                .orElse(Integer.MIN_VALUE);
        var highestCandidatePriority = candidates.stream()
                .mapToInt(candidate -> candidate.registration().priority())
                .max()
                .orElse(Integer.MIN_VALUE);
        var highestFailurePriority = failures.stream()
                .mapToInt(AdapterRegistration::priority)
                .max()
                .orElse(Integer.MIN_VALUE);
        var failureThreshold = supported.isEmpty() ? highestCandidatePriority : highestSupportedPriority;
        if (!failures.isEmpty() && highestFailurePriority >= failureThreshold) {
            return new Resolution(
                    new ControlEvaluation(
                            capability,
                            ControlSupport.UNSUPPORTED,
                            ControlRejectionReason.ADAPTER_ERROR,
                            Optional.empty(),
                            highestFailurePriority
                    ),
                    null
            );
        }

        if (!supported.isEmpty()) {
            var winners = supported.stream()
                    .filter(candidate -> candidate.registration().priority() == highestSupportedPriority)
                    .toList();
            if (winners.size() != 1) {
                return new Resolution(
                        new ControlEvaluation(
                                capability,
                                ControlSupport.UNSUPPORTED,
                                ControlRejectionReason.AMBIGUOUS_ADAPTER,
                                Optional.empty(),
                                highestSupportedPriority
                        ),
                        null
                );
            }
            var winner = winners.getFirst();
            return new Resolution(
                    new ControlEvaluation(
                            capability,
                            winner.support(),
                            ControlRejectionReason.SUPPORTED,
                            Optional.of(winner.registration().id()),
                            winner.registration().priority()
                    ),
                    winner.registration()
            );
        }

        if (candidates.isEmpty()) {
            return Resolution.rejected(capability, ControlRejectionReason.NO_ADAPTER);
        }
        var highestMatchingPriority = candidates.stream()
                .mapToInt(candidate -> candidate.registration().priority())
                .max()
                .orElse(Integer.MIN_VALUE);
        var rejected = candidates.stream()
                .filter(candidate -> candidate.registration().priority() == highestMatchingPriority)
                .toList();
        if (rejected.size() != 1) {
            return new Resolution(
                    new ControlEvaluation(
                            capability,
                            ControlSupport.UNSUPPORTED,
                            ControlRejectionReason.AMBIGUOUS_ADAPTER,
                            Optional.empty(),
                            highestMatchingPriority
                    ),
                    null
            );
        }
        var rejection = rejected.getFirst();
        return new Resolution(
                new ControlEvaluation(
                        capability,
                        ControlSupport.UNSUPPORTED,
                        rejection.reason(),
                        Optional.of(rejection.registration().id()),
                        rejection.registration().priority()
                ),
                null
        );
    }

    private static EnumMap<ControlDomain, ControlDirective> indexDirectives(List<ControlDirective> directives) {
        var indexed = new EnumMap<ControlDomain, ControlDirective>(ControlDomain.class);
        for (var directive : directives) {
            for (var domain : directive.domains()) {
                var previous = indexed.putIfAbsent(domain, directive);
                if (previous != null) {
                    throw new IllegalArgumentException("Multiple directives control domain " + domain);
                }
            }
        }
        return indexed;
    }

    private static void validateDirective(
            MinecraftServer server,
            LivingEntity subject,
            ControlDirective directive
    ) {
        if (directive instanceof ControlDirective.ForceTarget(var uuid)) {
            validateTarget(server, subject, uuid, directive.capability());
        } else if (directive instanceof ControlDirective.MoveTo moveTo) {
            validateDestination(server, subject, moveTo.destination(), directive.capability());
        } else if (directive instanceof ControlDirective.LookAt(var targetUuid)) {
            validateTarget(server, subject, targetUuid, directive.capability());
        } else if (directive instanceof ControlDirective.Guard guard) {
            validateDestination(server, subject, guard.destination(), directive.capability());
        }
    }

    public static void releasePlayerInputLeases(MinecraftServer server, UUID subjectId) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(subjectId, "subjectId");
        var state = stateIfPresent(server);
        if (state == null) return;
        synchronized (state) {
            var removal = state.leases.removePlayerInputBySubject(subjectId);
            reconcileRecovering(server, state, removal);
        }
        removeEmptyState(server, state);
    }

    private static @Nullable UUID referencedTarget(ControlDirective directive) {
        return switch (directive) {
            case ControlDirective.ForceTarget forceTarget -> forceTarget.targetUuid();
            case ControlDirective.LookAt lookAt -> lookAt.targetUuid();
            case ControlDirective.MoveTo moveTo -> moveTo.destination() instanceof ControlDestination.Entity(var uuid)
                    ? uuid : null;
            case ControlDirective.Guard guard -> guard.destination() instanceof ControlDestination.Entity(var uuid)
                    ? uuid : null;
            default -> null;
        };
    }

    private static void validateDestination(
            MinecraftServer server,
            LivingEntity subject,
            ControlDestination destination,
            ControlCapability capability
    ) {
        switch (destination) {
            case ControlDestination.Entity entity -> validateTarget(server, subject, entity.uuid(), capability);
            case ControlDestination.Position position -> {
                if (!subject.level().dimension().identifier().equals(position.dimension())) {
                    throw new ControlApplyException(
                            ControlRejectionReason.INVALID_DIRECTIVE,
                            capability,
                            "Fixed destination must be in the subject level"
                    );
                }
            }
        }
    }

    private static void validateTarget(
            MinecraftServer server,
            LivingEntity subject,
            UUID targetId,
            ControlCapability capability
    ) {
        var target = findLivingEntity(server, targetId);
        if (target == null || !target.isAlive() || target.isRemoved() || target == subject
                || target.level() != subject.level()) {
            throw new ControlApplyException(
                    ControlRejectionReason.INVALID_DIRECTIVE,
                    capability,
                    "Directive target must be a different loaded living entity in the subject level"
            );
        }
    }

    private static void releaseLease(MinecraftServer server, UUID leaseId) {
        var state = stateIfPresent(server);
        if (state == null) return;
        synchronized (state) {
            var removal = state.leases.remove(leaseId);
            reconcileRecovering(server, state, removal);
        }
        removeEmptyState(server, state);
    }

    private static boolean isLeaseActive(MinecraftServer server, UUID leaseId) {
        var state = stateIfPresent(server);
        return state != null && state.leases.isActive(leaseId);
    }

    private static void recordFailures(
            ServerState state,
            Iterable<UUID> leaseIds,
            ControlFailureReason reason
    ) {
        for (var leaseId : leaseIds) recordFailure(state, leaseId, reason);
    }

    private static void recordFailure(
            ServerState state,
            UUID leaseId,
            ControlFailureReason reason
    ) {
        var reference = state.handles.get(leaseId);
        var handle = reference == null ? null : reference.get();
        if (handle != null) handle.recordFailure(reason);
    }

    private static void reconcileRecovering(
            MinecraftServer server,
            ServerState state,
            RemovalResult removal
    ) {
        removal.merge(state.leases.removeInvalidImpressionGuards(
                Set.copyOf(removal.subjects()),
                server.overworld().getGameTime()
        ));
        var pending = new ArrayDeque<>(removal.subjects());
        var seen = new HashSet<UUID>();
        while (!pending.isEmpty()) {
            var subjectId = pending.removeFirst();
            if (!seen.add(subjectId)) continue;
            try {
                reconcileSubject(server, state, subjectId);
            } catch (BindingFailure failure) {
                AcademyCraft.LOGGER.error(
                        "Mental control adapter failed to activate for lease {}",
                        failure.leaseId(),
                        failure.getCause()
                );
                var failed = state.leases.remove(failure.leaseId());
                removal.merge(failed);
                pending.addAll(failed.subjects());
                seen.remove(subjectId);
            }
        }
        reconcileTargets(server, state, removal);
    }

    private static void reconcileSubject(MinecraftServer server, ServerState state, UUID subjectId) {
        var subject = findLivingEntity(server, subjectId);
        var now = subject != null ? subject.level().getGameTime() : server.overworld().getGameTime();
        var relation = state.leases.effective(
                subjectId,
                ControlCapability.RELATION_CONTROL,
                now
        );
        if (subject == null) {
            state.targetWhitelist.retainRelation(subjectId, null);
            closeBindingsForSubject(state, subjectId);
            return;
        }
        for (var capability : ControlCapability.values()) {
            var key = new BindingKey(subjectId, capability);
            var current = state.activeBindings.get(key);
            var effective = state.leases.effective(subjectId, capability, now);
            if (current != null && effective != null && current.leaseId().equals(effective.leaseId())) {
                continue;
            }
            if (current != null) {
                state.activeBindings.remove(key);
                closeBinding(current);
            }
            if (effective == null) continue;

            var controller = server.getPlayerList().getPlayer(effective.controllerId());
            var selection = effective.selection();
            if (controller == null || selection == null) {
                throw new BindingFailure(
                        effective.leaseId(),
                        capability,
                        new IllegalStateException("Effective control has no live controller or adapter selection")
                );
            }
            var context = new ControlContext(
                    server,
                    effective.leaseId(),
                    controller,
                    subject,
                    effective.source(),
                    effective.priority(),
                    effective.expiresAt()
            );
            try {
                var binding = Objects.requireNonNull(
                        selection.adapter().activate(context, effective.directive()),
                        "Mental control adapter returned a null binding"
                );
                state.activeBindings.put(
                        key,
                        new ActiveBinding(effective.leaseId(), capability, new SafeBinding(binding))
                );
            } catch (Throwable throwable) {
                throw new BindingFailure(effective.leaseId(), capability, throwable);
            }
        }
        state.targetWhitelist.retainRelation(
                subjectId,
                relation != null
                        && relation.directive() instanceof ControlDirective.ImpressionAlliance
                        ? relation.leaseId()
                        : null
        );
    }

    private static void closeBindingsForSubject(ServerState state, UUID subjectId) {
        var iterator = state.activeBindings.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!entry.getKey().subjectId().equals(subjectId)) continue;
            iterator.remove();
            closeBinding(entry.getValue());
        }
    }

    private static void closeAllBindings(ServerState state) {
        state.activeBindings.values().forEach(MentalControlRuntime::closeBinding);
        state.activeBindings.clear();
    }

    private static void closeBinding(ActiveBinding binding) {
        try {
            binding.binding().close();
        } catch (Throwable throwable) {
            AcademyCraft.LOGGER.error("Mental control binding {} failed to close", binding.leaseId(), throwable);
        }
    }

    private static void reconcileTargets(MinecraftServer server, ServerState state, RemovalResult removal) {
        for (var subjectId : removal.subjects()) {
            var entity = findLivingEntity(server, subjectId);
            if (!(entity instanceof Mob mob)) continue;
            var replacement = state.leases.forcedTarget(mob.getUUID(), mob.level().getGameTime());
            if (replacement != null) {
                maintainTarget(mob);
                continue;
            }
            var removedTargets = removal.removedTargets().get(subjectId);
            if (removedTargets == null || removedTargets.isEmpty()) continue;
            var currentTarget = getRawTarget(mob);
            if (currentTarget != null && removedTargets.contains(currentTarget.getUUID())) mob.setTarget(null);
            var brainTarget = mob.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET);
            if (brainTarget != null && brainTarget.isPresent()
                    && removedTargets.contains(brainTarget.get().getUUID())) {
                mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            }
        }
    }

    private static @Nullable LivingEntity getRawTarget(Mob mob) {
        return mob instanceof MentalControlMobAccess access
                ? access.academy$getRawMentalControlTarget()
                : mob.getTargetUnchecked();
    }

    private static ServerState state(MinecraftServer server) {
        synchronized (SERVER_STATES) {
            return SERVER_STATES.computeIfAbsent(server, ignored -> new ServerState());
        }
    }

    private static @Nullable ServerState stateIfPresent(@Nullable MinecraftServer server) {
        return server == null ? null : SERVER_STATES.get(server);
    }

    private static List<Map.Entry<MinecraftServer, ServerState>> serverStateSnapshot() {
        synchronized (SERVER_STATES) {
            var snapshot = new ArrayList<Map.Entry<MinecraftServer, ServerState>>(SERVER_STATES.size());
            SERVER_STATES.forEach((server, state) -> snapshot.add(Map.entry(server, state)));
            return snapshot;
        }
    }

    private static void removeEmptyState(MinecraftServer server, ServerState state) {
        if (!state.leases.isEmpty() || !state.activeBindings.isEmpty()) return;
        synchronized (SERVER_STATES) {
            SERVER_STATES.remove(server, state);
        }
    }

    record AdapterRegistration(Identifier id, int priority, MentalControlAdapter adapter) {
    }

    record CapabilityCandidate(
            AdapterRegistration registration,
            ControlSupport support,
            ControlRejectionReason reason
    ) {
    }

    record Resolution(ControlEvaluation evaluation, @Nullable AdapterRegistration registration) {
        private static Resolution rejected(
                ControlCapability capability,
                ControlRejectionReason reason
        ) {
            return new Resolution(
                    new ControlEvaluation(
                            capability,
                            ControlSupport.UNSUPPORTED,
                            reason,
                            Optional.empty(),
                            Integer.MIN_VALUE
                    ),
                    null
            );
        }
    }

    private record AdapterSelection(
            Identifier id,
            int priority,
            ControlSupport support,
            MentalControlAdapter adapter
    ) {
    }

    private static final class ServerState {
        private final LeaseTable leases = new LeaseTable();
        private final Map<BindingKey, ActiveBinding> activeBindings = new HashMap<>();
        private final TargetWhitelist targetWhitelist = new TargetWhitelist();
        private final Map<UUID, UUID> guardTargets = new HashMap<>();
        private final Map<UUID, WeakReference<RuntimeHandle>> handles = new HashMap<>();
    }

    static final class TargetWhitelist {
        private final Map<UUID, TargetAuthorization> retaliations = new ConcurrentHashMap<>();

        void authorize(
                UUID subjectId,
                UUID targetId,
                UUID relationLeaseId,
                long expiresAt
        ) {
            retaliations.put(
                    subjectId,
                    new TargetAuthorization(targetId, relationLeaseId, expiresAt)
            );
        }

        boolean allows(
                UUID subjectId,
                UUID targetId,
                UUID relationLeaseId,
                long now
        ) {
            var authorization = retaliations.get(subjectId);
            if (authorization == null) return false;
            if (authorization.expiresAt() <= now
                    || !authorization.relationLeaseId().equals(relationLeaseId)) {
                retaliations.remove(subjectId, authorization);
                return false;
            }
            return authorization.targetId().equals(targetId);
        }

        void expire(long now) {
            retaliations.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        }

        void retainRelation(UUID subjectId, @Nullable UUID relationLeaseId) {
            var authorization = retaliations.get(subjectId);
            if (authorization != null
                    && !authorization.relationLeaseId().equals(relationLeaseId)) {
                retaliations.remove(subjectId, authorization);
            }
        }

        void releaseEntity(UUID entityId) {
            retaliations.remove(entityId);
            retaliations.entrySet().removeIf(entry -> entry.getValue().targetId().equals(entityId));
        }
    }

    private record TargetAuthorization(UUID targetId, UUID relationLeaseId, long expiresAt) {
    }

    private record BindingKey(UUID subjectId, ControlCapability capability) {
    }

    private record ActiveBinding(UUID leaseId, ControlCapability capability, SafeBinding binding) {
    }

    private static final class SafeBinding implements ControlBinding {
        private final ControlBinding delegate;
        private boolean closed;

        private SafeBinding(ControlBinding delegate) {
            this.delegate = delegate;
        }

        @Override
        public void tick() {
            if (!closed) delegate.tick();
        }

        @Override
        public boolean isComplete() {
            return closed || delegate.isComplete();
        }

        @Override
        public void beforeNavigationTick() {
            if (!closed) delegate.beforeNavigationTick();
        }

        @Override
        public void beforeMoveControlTick() {
            if (!closed) delegate.beforeMoveControlTick();
        }

        @Override
        public void beforeLookControlTick() {
            if (!closed) delegate.beforeLookControlTick();
        }

        @Override
        public Optional<ControlFailureReason> failureReason() {
            return closed ? Optional.empty() : delegate.failureReason();
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            delegate.close();
        }
    }

    private static final class BindingFailure extends RuntimeException {
        private final UUID leaseId;
        private final ControlCapability capability;

        private BindingFailure(UUID leaseId, ControlCapability capability, Throwable cause) {
            super(cause);
            this.leaseId = leaseId;
            this.capability = capability;
        }

        private UUID leaseId() {
            return leaseId;
        }

        private ControlCapability capability() {
            return capability;
        }
    }

    private static final class RuntimeHandle implements ControlHandle {
        private final WeakReference<MinecraftServer> server;
        private final UUID id;
        private final AtomicBoolean closed = new AtomicBoolean();
        private volatile ControlFailureReason failureReason;

        private RuntimeHandle(MinecraftServer server, UUID id) {
            this.server = new WeakReference<>(server);
            this.id = id;
        }

        @Override
        public UUID id() {
            return id;
        }

        @Override
        public boolean isClosed() {
            var currentServer = server.get();
            return closed.get() || currentServer == null || !isLeaseActive(currentServer, id);
        }

        @Override
        public Optional<ControlFailureReason> failureReason() {
            return Optional.ofNullable(failureReason);
        }

        private void recordFailure(ControlFailureReason reason) {
            if (failureReason == null) failureReason = reason;
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) return;
            var currentServer = server.get();
            if (currentServer != null) releaseLease(currentServer, id);
        }
    }

    record LeaseInput(
            UUID controllerId,
            UUID subjectId,
            Identifier source,
            int priority,
            long expiresAt,
            Identifier controllerDimension,
            Identifier subjectDimension,
            EnumMap<ControlDomain, ControlDirective> directives,
            Map<ControlCapability, AdapterSelection> selections,
            @Nullable UUID guardianRelationLeaseId
    ) {
        LeaseInput {
            Objects.requireNonNull(controllerId, "controllerId");
            Objects.requireNonNull(subjectId, "subjectId");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(controllerDimension, "controllerDimension");
            Objects.requireNonNull(subjectDimension, "subjectDimension");
            directives = new EnumMap<>(Objects.requireNonNull(directives, "directives"));
            selections = Map.copyOf(Objects.requireNonNull(selections, "selections"));
        }

        LeaseInput(
                UUID controllerId,
                UUID subjectId,
                Identifier source,
                int priority,
                long expiresAt,
                Identifier controllerDimension,
                Identifier subjectDimension,
                EnumMap<ControlDomain, ControlDirective> directives
        ) {
            this(
                    controllerId,
                    subjectId,
                    source,
                    priority,
                    expiresAt,
                    controllerDimension,
                    subjectDimension,
                    directives,
                    Map.of(),
                    null
            );
        }

        LeaseInput(
                UUID controllerId,
                UUID subjectId,
                Identifier source,
                int priority,
                long expiresAt,
                Identifier controllerDimension,
                Identifier subjectDimension,
                EnumMap<ControlDomain, ControlDirective> directives,
                @Nullable UUID guardianRelationLeaseId
        ) {
            this(
                    controllerId,
                    subjectId,
                    source,
                    priority,
                    expiresAt,
                    controllerDimension,
                    subjectDimension,
                    directives,
                    Map.of(),
                    guardianRelationLeaseId
            );
        }
    }

    record LeaseSnapshot(
            UUID id,
            UUID controllerId,
            UUID subjectId,
            Identifier source,
            Identifier controllerDimension,
            Identifier subjectDimension,
            Set<UUID> referencedTargets,
            @Nullable UUID guardianRelationLeaseId
    ) {
    }

    static final class LeaseTable {
        private static final Comparator<DomainLease> WINNER_ORDER = (left, right) -> {
            var priorityOrder = Integer.compare(right.priority(), left.priority());
            if (priorityOrder != 0) return priorityOrder;
            var sequenceOrder = Long.compare(right.sequence(), left.sequence());
            if (sequenceOrder != 0) return sequenceOrder;
            return left.leaseId().compareTo(right.leaseId());
        };

        private final Map<UUID, SubjectState> subjects = new HashMap<>();
        private final Map<UUID, LeaseRecord> leases = new LinkedHashMap<>();
        private final PriorityQueue<ExpiryEntry> expirations = new PriorityQueue<>();
        private long sequence;

        synchronized UUID add(LeaseInput input) {
            var leaseId = UUID.randomUUID();
            var leaseSequence = ++sequence;
            var record = new LeaseRecord(leaseId, input, leaseSequence);
            leases.put(leaseId, record);
            var subject = subjects.computeIfAbsent(input.subjectId(), ignored -> new SubjectState());
            for (var entry : input.directives().entrySet()) {
                var domainLease = new DomainLease(
                        leaseId,
                        input.controllerId(),
                        input.source(),
                        input.priority(),
                        leaseSequence,
                        input.expiresAt(),
                        entry.getValue()
                );
                var replaced = subject.domain(entry.getKey()).put(domainLease);
                if (replaced != null) detach(replaced, entry.getKey());
                record.entries().put(entry.getKey(), domainLease);
            }
            expirations.add(new ExpiryEntry(input.expiresAt(), leaseSequence, leaseId));
            return leaseId;
        }

        synchronized boolean isActive(UUID leaseId) {
            return leases.containsKey(leaseId);
        }

        synchronized boolean isEmpty() {
            return leases.isEmpty();
        }

        synchronized Set<UUID> subjectIds() {
            return Set.copyOf(subjects.keySet());
        }

        synchronized boolean isFrozen(UUID subjectId, long now) {
            var effective = effective(subjectId, ControlCapability.FREEZE_AI, now);
            return effective != null && effective.directive() instanceof ControlDirective.FreezeAi;
        }

        synchronized @Nullable UUID forcedTarget(UUID subjectId, long now) {
            var effective = effective(subjectId, ControlCapability.FORCE_TARGET, now);
            return effective != null && effective.directive() instanceof ControlDirective.ForceTarget(var targetUuid)
                    ? targetUuid
                    : null;
        }

        synchronized List<ImpressionSubject> impressionSubjects(UUID controllerId, long now) {
            var result = new ArrayList<ImpressionSubject>();
            for (var subjectId : subjects.keySet()) {
                var relation = effective(subjectId, ControlCapability.RELATION_CONTROL, now);
                if (relation != null && relation.controllerId().equals(controllerId)
                        && relation.directive() instanceof ControlDirective.ImpressionAlliance) {
                    result.add(new ImpressionSubject(subjectId, relation.expiresAt()));
                }
            }
            return List.copyOf(result);
        }

        synchronized boolean hasImpressionAlliance(UUID controllerId, UUID subjectId, long now) {
            var relation = effective(subjectId, ControlCapability.RELATION_CONTROL, now);
            return relation != null && relation.controllerId().equals(controllerId)
                    && relation.directive() instanceof ControlDirective.ImpressionAlliance;
        }

        synchronized boolean hasActiveControl(UUID controllerId, UUID subjectId, long now) {
            return leases.values().stream().anyMatch(lease ->
                    lease.input().controllerId().equals(controllerId)
                            && lease.input().subjectId().equals(subjectId)
                            && lease.input().expiresAt() > now
                            && !lease.entries().isEmpty());
        }

        synchronized boolean hasImpressionAlliance(
                UUID controllerId,
                UUID subjectId,
                @Nullable UUID relationLeaseId,
                long now
        ) {
            if (relationLeaseId == null) return false;
            var relation = effective(subjectId, ControlCapability.RELATION_CONTROL, now);
            return relation != null && relation.leaseId().equals(relationLeaseId)
                    && relation.controllerId().equals(controllerId)
                    && relation.directive() instanceof ControlDirective.ImpressionAlliance;
        }

        synchronized RemovalResult removeInvalidImpressionGuards(Set<UUID> subjectIds, long now) {
            if (subjectIds.isEmpty()) return new RemovalResult();
            var invalid = leases.values().stream()
                    .filter(lease -> subjectIds.contains(lease.input().subjectId()))
                    .filter(lease -> lease.input().source().equals(IMPRESSION_GUARD_SOURCE))
                    .filter(lease -> !hasImpressionAlliance(
                            lease.input().controllerId(),
                            lease.input().subjectId(),
                            lease.input().guardianRelationLeaseId(),
                            now
                    ))
                    .map(LeaseRecord::id)
                    .collect(Collectors.toUnmodifiableSet());
            return removeAll(invalid);
        }

        synchronized @Nullable EffectiveDirective effective(
                UUID subjectId,
                ControlCapability capability,
                long now
        ) {
            DomainLease first = null;
            for (var domain : capability.domains()) {
                var winner = winner(subjectId, domain, now);
                if (winner == null || winner.directive().capability() != capability) return null;
                if (first == null) {
                    first = winner;
                } else if (!first.leaseId().equals(winner.leaseId())) {
                    return null;
                }
            }
            if (first == null) return null;
            var record = leases.get(first.leaseId());
            if (record == null) return null;
            return new EffectiveDirective(
                    first.leaseId(),
                    first.controllerId(),
                    first.source(),
                    first.priority(),
                    first.expiresAt(),
                    first.directive(),
                    record.input().selections().get(capability)
            );
        }

        synchronized RemovalResult remove(UUID leaseId) {
            var result = new RemovalResult();
            removeInto(leaseId, result);
            return result;
        }

        synchronized RemovalResult removeAll(Set<UUID> leaseIds) {
            var result = new RemovalResult();
            leaseIds.forEach(id -> removeInto(id, result));
            return result;
        }

        synchronized RemovalResult removeByController(UUID controllerId) {
            var ids = leases.values().stream()
                    .filter(lease -> lease.input().controllerId().equals(controllerId))
                    .map(LeaseRecord::id)
                    .toList();
            return removeAll(Set.copyOf(ids));
        }

        synchronized RemovalResult removeBySubject(UUID subjectId) {
            var ids = leases.values().stream()
                    .filter(lease -> lease.input().subjectId().equals(subjectId))
                    .map(LeaseRecord::id)
                    .toList();
            return removeAll(Set.copyOf(ids));
        }


        synchronized RemovalResult removeByControllerSourceSubject(
                UUID controllerId,
                Identifier source,
                UUID subjectId
        ) {
            var ids = leases.values().stream()
                    .filter(lease -> lease.input().subjectId().equals(subjectId))
                    .filter(lease -> lease.input().controllerId().equals(controllerId))
                    .filter(lease -> lease.input().source().equals(source))
                    .map(LeaseRecord::id)
                    .toList();
            return removeAll(Set.copyOf(ids));
        }

        synchronized RemovalResult expire(long now) {
            var result = new RemovalResult();
            while (!expirations.isEmpty() && expirations.peek().expiresAt() <= now) {
                var expiration = expirations.remove();
                var lease = leases.get(expiration.leaseId());
                if (lease != null && lease.input().expiresAt() <= now) {
                    removeInto(expiration.leaseId(), result);
                }
            }
            return result;
        }

        synchronized RemovalResult clear() {
            return removeAll(Set.copyOf(leases.keySet()));
        }

        synchronized List<LeaseSnapshot> snapshot() {
            return leases.values().stream().map(lease -> new LeaseSnapshot(
                    lease.id(),
                    lease.input().controllerId(),
                    lease.input().subjectId(),
                    lease.input().source(),
                    lease.input().controllerDimension(),
                    lease.input().subjectDimension(),
                    lease.entries().values().stream()
                            .map(DomainLease::directive)
                            .map(MentalControlRuntime::referencedTarget)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toUnmodifiableSet()),
                    lease.input().guardianRelationLeaseId()
            )).toList();
        }

        synchronized RemovalResult removePlayerInputBySubject(UUID subjectId) {
            var ids = leases.values().stream()
                    .filter(lease -> lease.input().subjectId().equals(subjectId))
                    .filter(lease -> lease.input().directives().containsKey(ControlDomain.MOVEMENT)
                            || lease.input().directives().containsKey(ControlDomain.VIEW)
                            || lease.input().directives().containsKey(ControlDomain.ACTION))
                    .map(LeaseRecord::id)
                    .toList();
            return removeAll(Set.copyOf(ids));
        }

        synchronized TableSnapshot snapshotState() {
            var records = leases.values().stream()
                    .map(lease -> new LeaseStateSnapshot(
                            lease.id(),
                            lease.input(),
                            lease.sequence(),
                            new EnumMap<>(lease.entries())
                    ))
                    .toList();
            return new TableSnapshot(sequence, records);
        }

        synchronized void restore(TableSnapshot snapshot) {
            subjects.clear();
            leases.clear();
            expirations.clear();
            sequence = snapshot.sequence();
            for (var saved : snapshot.leases()) {
                var record = new LeaseRecord(saved.id(), saved.input(), saved.sequence());
                record.entries().putAll(saved.entries());
                leases.put(record.id(), record);
                var subject = subjects.computeIfAbsent(
                        record.input().subjectId(),
                        ignored -> new SubjectState()
                );
                for (var entry : record.entries().entrySet()) {
                    subject.domain(entry.getKey()).put(entry.getValue());
                }
                expirations.add(new ExpiryEntry(
                        record.input().expiresAt(),
                        record.sequence(),
                        record.id()
                ));
            }
        }

        private @Nullable DomainLease winner(UUID subjectId, ControlDomain domain, long now) {
            var subject = subjects.get(subjectId);
            if (subject == null) return null;
            return subject.domainIfPresent(domain).map(state -> state.winner(now)).orElse(null);
        }

        private void detach(DomainLease lease, ControlDomain domain) {
            var oldRecord = leases.get(lease.leaseId());
            if (oldRecord == null) return;
            oldRecord.entries().remove(domain, lease);
            if (oldRecord.entries().isEmpty()) leases.remove(oldRecord.id());
        }

        private void removeInto(UUID leaseId, RemovalResult result) {
            var lease = leases.remove(leaseId);
            if (lease == null) return;
            var subjectId = lease.input().subjectId();
            result.addSubject(subjectId);
            var subject = subjects.get(subjectId);
            if (subject == null) return;
            for (var entry : lease.entries().entrySet()) {
                subject.domain(entry.getKey()).remove(entry.getValue());
                if (entry.getValue().directive() instanceof ControlDirective.ForceTarget(var targetUuid)) {
                    result.addTarget(subjectId, targetUuid);
                }
            }
            subject.removeEmptyDomains();
            if (subject.isEmpty()) subjects.remove(subjectId);
        }

        private static final class SubjectState {
            private final EnumMap<ControlDomain, DomainState> domains = new EnumMap<>(ControlDomain.class);

            private DomainState domain(ControlDomain domain) {
                return domains.computeIfAbsent(domain, ignored -> new DomainState());
            }

            private Optional<DomainState> domainIfPresent(ControlDomain domain) {
                return Optional.ofNullable(domains.get(domain));
            }

            private void removeEmptyDomains() {
                domains.values().removeIf(DomainState::isEmpty);
            }

            private boolean isEmpty() {
                return domains.isEmpty();
            }
        }

        private static final class DomainState {
            private final Map<ReplacementKey, DomainLease> bySource = new HashMap<>();
            private final NavigableSet<DomainLease> ordered = new TreeSet<>(WINNER_ORDER);
            private @Nullable DomainLease currentWinner;

            private @Nullable DomainLease put(DomainLease lease) {
                var key = new ReplacementKey(lease.controllerId(), lease.source());
                var replaced = bySource.put(key, lease);
                if (replaced != null) ordered.remove(replaced);
                ordered.add(lease);
                refreshWinner();
                return replaced;
            }

            private void remove(DomainLease lease) {
                var key = new ReplacementKey(lease.controllerId(), lease.source());
                bySource.remove(key, lease);
                ordered.remove(lease);
                refreshWinner();
            }

            private @Nullable DomainLease winner(long now) {
                return currentWinner != null && currentWinner.expiresAt() > now ? currentWinner : null;
            }

            private boolean isEmpty() {
                return ordered.isEmpty();
            }

            private void refreshWinner() {
                currentWinner = ordered.isEmpty() ? null : ordered.first();
            }
        }
    }

    private record EffectiveDirective(
            UUID leaseId,
            UUID controllerId,
            Identifier source,
            int priority,
            long expiresAt,
            ControlDirective directive,
            @Nullable AdapterSelection selection
    ) {
    }

    private record ImpressionSubject(UUID subjectId, long expiresAt) {
    }

    private record TableSnapshot(long sequence, List<LeaseStateSnapshot> leases) {
    }

    private record LeaseStateSnapshot(
            UUID id,
            LeaseInput input,
            long sequence,
            EnumMap<ControlDomain, DomainLease> entries
    ) {
    }

    private record LeaseRecord(
            UUID id,
            LeaseInput input,
            long sequence,
            EnumMap<ControlDomain, DomainLease> entries
    ) {
        private LeaseRecord(UUID id, LeaseInput input, long sequence) {
            this(id, input, sequence, new EnumMap<>(ControlDomain.class));
        }
    }

    private record DomainLease(
            UUID leaseId,
            UUID controllerId,
            Identifier source,
            int priority,
            long sequence,
            long expiresAt,
            ControlDirective directive
    ) {
    }

    private record ReplacementKey(UUID controllerId, Identifier source) {
    }

    private record ExpiryEntry(long expiresAt, long sequence, UUID leaseId)
            implements Comparable<ExpiryEntry> {
        @Override
        public int compareTo(ExpiryEntry other) {
            var expiryOrder = Long.compare(expiresAt, other.expiresAt);
            return expiryOrder != 0 ? expiryOrder : Long.compare(sequence, other.sequence);
        }
    }

    static final class RemovalResult {
        private final Map<UUID, Set<UUID>> removedTargets = new HashMap<>();
        private final Set<UUID> subjects = new HashSet<>();

        private void addTarget(UUID subjectId, UUID targetId) {
            removedTargets.computeIfAbsent(subjectId, ignored -> new HashSet<>()).add(targetId);
        }

        private void addSubject(UUID subjectId) {
            subjects.add(subjectId);
        }

        void merge(RemovalResult other) {
            subjects.addAll(other.subjects);
            other.removedTargets.forEach((subjectId, targets) ->
                    removedTargets.computeIfAbsent(subjectId, ignored -> new HashSet<>()).addAll(targets));
        }

        Map<UUID, Set<UUID>> removedTargets() {
            return removedTargets;
        }

        Set<UUID> subjects() {
            return subjects;
        }
    }
}
