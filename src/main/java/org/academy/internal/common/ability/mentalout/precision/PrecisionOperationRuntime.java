package org.academy.internal.common.ability.mentalout.precision;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;
import org.academy.api.common.entitycontrol.AttackDecision;
import org.academy.api.common.entitycontrol.ControlCapability;
import org.academy.api.common.entitycontrol.ControlDestination;
import org.academy.api.common.entitycontrol.ControlDirective;
import org.academy.api.common.entitycontrol.ControlFailureReason;
import org.academy.api.common.entitycontrol.ControlHandle;
import org.academy.api.common.entitycontrol.ControlRequest;
import org.academy.api.common.entitycontrol.MentalControlApi;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.mentalout.MentalIntrusionManager;
import org.academy.internal.common.ability.mentalout.MentaloutConfig;
import org.academy.internal.common.ability.mentalout.MentaloutControlContext;
import org.academy.internal.common.ability.mentalout.control.MentalControlRuntime;
import org.academy.internal.common.ability.mentalout.control.MentalPerceptionRuntime;
import org.academy.internal.common.ability.mentalout.skills.MentaloutTargeting;
import org.academy.internal.common.world.damagesource.FriendlyFireSetting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PrecisionOperationRuntime {
    public static final int PRIORITY = 200;
    private static final Map<UUID, ActiveContext[]> ACTIVE = new HashMap<>();

    private PrecisionOperationRuntime() {
    }

    public static ExecutionResult execute(
            ServerPlayer player,
            int slot,
            CompiledPrecisionProgram program
    ) {
        if (player == null || slot < 0 || slot >= 4 || program == null) {
            return ExecutionResult.failed(PrecisionGraph.Diagnostic.ACTION_FAILED);
        }
        var slots = ACTIVE.computeIfAbsent(player.getUUID(), _ -> new ActiveContext[4]);
        var previous = slots[slot];
        var skill = Skills.PRECISION_OPERATION.get();
        if (!skill.isEnabled(player)) {
            return ExecutionResult.failed(PrecisionGraph.Diagnostic.SKILL_UNAVAILABLE);
        }
        var level = Math.clamp(skill.getLevel(player), 0, 2);
        var targetLimit = switch (level) {
            case 0 -> 4;
            case 1 -> 6;
            default -> 8;
        };
        var evaluated = evaluate(player, program, targetLimit, player.level().getGameTime());
        if (!evaluated.valid()) {
            return ExecutionResult.failed(
                    evaluated.diagnostic, evaluated.nodeId, evaluated.port, evaluated.affectedCount);
        }

        var existingCost = activeCost(slots);
        var removedSubjects = removedSubjects(evaluated.actions);
        var previousCost = previous == null ? 0.0f : previous.cost();
        var projectedCost = Math.max(
                0.0f,
                existingCost - previousCost - subjectCostExceptSlot(slots, slot, removedSubjects)
        )
                + evaluated.cost;
        var system = AbilitySystemServer.getSystem(player);
        if (!system.canCastWithPermanentOccupations(
                player,
                skill,
                0.0f,
                Map.of(skill, projectedCost)
        )) {
            return ExecutionResult.failed(PrecisionGraph.Diagnostic.INSUFFICIENT_CP);
        }

        var activeActions = new ArrayList<ActiveAction>();
        var applyingNodeId = -1;
        try {
            // A repeated cast refreshes this slot. Every action has its own completion condition,
            // so clicking the skill is never interpreted as a manual off switch.
            slots[slot] = null;
            if (previous != null) previous.close(player);
            for (var action : evaluated.actions) {
                applyingNodeId = action.nodeId;
                var subjectHandles = new HashMap<UUID, List<AutoCloseable>>();
                var subjectCosts = new HashMap<UUID, Float>();
                var fixedCost = 0.0f;
                UUID intrusionSession = null;
                try {
                    switch (action.kind) {
                    case TARGET_MISIDENTIFICATION -> applyMisidentification(
                            player, action.entities, action.entity, action.expiresAt,
                            subjectHandles, subjectCosts);
                    case MENTAL_STUPOR -> applyDirective(
                            player, action.entities, new ControlDirective.FreezeAi(),
                            PrecisionGraph.NodeKind.MENTAL_STUPOR, action.expiresAt,
                            subjectHandles, subjectCosts);
                    case IMPRESSION_MANIPULATION -> applyDirective(
                            player, action.entities, new ControlDirective.ImpressionAlliance(),
                            PrecisionGraph.NodeKind.IMPRESSION_MANIPULATION, action.expiresAt,
                            subjectHandles, subjectCosts);
                    case PERCEPTION_MASK -> applyPerception(
                            player, action.entities, action.entity, action.expiresAt,
                            subjectHandles, subjectCosts);
                    case START_INTRUSION -> {
                        intrusionSession = MentalIntrusionManager.startPrecision(
                                player,
                                action.entity,
                                action.expiresAt
                        );
                        if (intrusionSession == null) throw new IllegalStateException("Intrusion rejected");
                        fixedCost += MentaloutConfig.mentalIntrusionCost(
                                player,
                                Math.clamp(Skills.MENTAL_INTRUSION.get().getLevel(player), 0, 2)
                        );
                    }
                    case END_INTRUSION, REMOVE_CONTROL -> {
                    }
                    case PATH_TO -> applyDirective(
                            player, action.entities, new ControlDirective.MoveTo(action.destination),
                            PrecisionGraph.NodeKind.PATH_TO, action.expiresAt,
                            subjectHandles, subjectCosts);
                    case VIEW_CONTROL -> applyDirective(
                            player, action.entities, new ControlDirective.LookAt(action.entity.getUUID()),
                            PrecisionGraph.NodeKind.VIEW_CONTROL, action.expiresAt,
                            subjectHandles, subjectCosts);
                    case GUARD_MODE -> applyDirective(
                            player, action.entities, new ControlDirective.Guard(action.destination),
                            PrecisionGraph.NodeKind.GUARD_MODE, action.expiresAt,
                            subjectHandles, subjectCosts);
                    }
                } catch (RuntimeException exception) {
                    subjectHandles.values().forEach(PrecisionOperationRuntime::closeReverse);
                    if (intrusionSession != null) {
                        MentalIntrusionManager.stopPrecision(player, intrusionSession);
                    }
                    throw exception;
                }
                if (!subjectHandles.isEmpty() || fixedCost > 0.0f || intrusionSession != null) {
                    activeActions.add(new ActiveAction(
                            action.nodeId,
                            action.kind,
                            action.expiresAt,
                            new HashMap<>(subjectHandles),
                            new HashMap<>(subjectCosts),
                            fixedCost,
                            intrusionSession,
                            action.dependencyIds()
                    ));
                }
            }
            releaseSubjects(player, removedSubjects);
            existingCost = activeCost(slots);
            var appliedCost = existingCost;
            for (var action : activeActions) appliedCost += action.cost();
            if (!system.replacePermanentOccupation(player.getUUID(), appliedCost, skill)) {
                throw new IllegalStateException("CP occupation rejected");
            }
            slots[slot] = activeActions.isEmpty() ? null : new ActiveContext(activeActions);
            if (evaluated.endIntrusion) MentalIntrusionManager.stopAny(player);
            if (java.util.Arrays.stream(slots).allMatch(java.util.Objects::isNull)) {
                ACTIVE.remove(player.getUUID());
            }
            return activeActions.isEmpty() ? ExecutionResult.completed() : ExecutionResult.started();
        } catch (RuntimeException exception) {
            activeActions.forEach(action -> action.close(player));
            system.replacePermanentOccupation(player.getUUID(), activeCost(slots), skill);
            return ExecutionResult.failed(PrecisionGraph.Diagnostic.ACTION_FAILED, applyingNodeId, -1, 0);
        }
    }

    public static void tick(MinecraftServer server) {
        var now = server.overworld().getGameTime();
        for (var entry : List.copyOf(ACTIVE.entrySet())) {
            var player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !player.isAlive() || !Skills.PRECISION_OPERATION.get().isEnabled(player)) {
                releaseController(entry.getKey(), player);
                continue;
            }
            var changed = false;
            for (var slot = 0; slot < entry.getValue().length; slot++) {
                var context = entry.getValue()[slot];
                if (context == null) continue;
                var tick = context.tick(player, now);
                changed |= tick.changed();
                for (var failure : tick.failures()) {
                    PrecisionOperationManager.runtimeError(
                            player, slot, failure.diagnostic(), failure.nodeId(), failure.affectedCount());
                }
                if (context.empty()) {
                    entry.getValue()[slot] = null;
                    if (tick.failures().isEmpty()) {
                        PrecisionOperationManager.runtimeCompleted(player, slot);
                    }
                }
            }
            if (changed) {
                var remaining = activeCost(entry.getValue());
                var system = AbilitySystemServer.getSystem(player);
                if (remaining <= 0.0f) {
                    system.releaseMaintenanceOccupation(
                            player.getUUID(), Skills.PRECISION_OPERATION.get().getKeyString());
                } else {
                    system.replacePermanentOccupation(
                            player.getUUID(), remaining, Skills.PRECISION_OPERATION.get());
                }
            }
            if (java.util.Arrays.stream(entry.getValue()).allMatch(java.util.Objects::isNull)) {
                ACTIVE.remove(entry.getKey());
            }
        }
    }

    public static void releaseController(UUID controllerId) {
        releaseController(controllerId, null);
    }

    public static void releaseController(ServerPlayer player) {
        if (player != null) releaseController(player.getUUID(), player);
    }

    public static void releaseEntity(MinecraftServer server, UUID entityId) {
        for (var entry : List.copyOf(ACTIVE.entrySet())) {
            var player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;
            var changed = false;
            for (var slot = 0; slot < entry.getValue().length; slot++) {
                var context = entry.getValue()[slot];
                if (context == null) continue;
                changed |= context.releaseEntity(player, entityId);
                if (context.empty()) entry.getValue()[slot] = null;
            }
            if (changed) {
                var remaining = activeCost(entry.getValue());
                var system = AbilitySystemServer.getSystem(player);
                if (remaining <= 0.0f) {
                    system.releaseMaintenanceOccupation(
                            player.getUUID(), Skills.PRECISION_OPERATION.get().getKeyString());
                } else {
                    system.replacePermanentOccupation(
                            player.getUUID(), remaining, Skills.PRECISION_OPERATION.get());
                }
            }
            if (java.util.Arrays.stream(entry.getValue()).allMatch(java.util.Objects::isNull)) {
                ACTIVE.remove(entry.getKey());
            }
        }
    }

    public static void clear(MinecraftServer server) {
        for (var controllerId : List.copyOf(ACTIVE.keySet())) {
            releaseController(controllerId, server.getPlayerList().getPlayer(controllerId));
        }
        ACTIVE.clear();
    }

    private static Evaluation evaluate(
            ServerPlayer player,
            CompiledPrecisionProgram program,
            int targetLimit,
            long now
    ) {
        var values = new HashMap<Integer, Object>();
        var actions = new ArrayList<PendingAction>();
        var uniqueTargets = new HashSet<UUID>();
        var cost = 0.0f;
        var endIntrusion = false;
        for (var node : program.order()) {
            try {
                switch (node.kind()) {
                    case CASTER -> values.put(node.id(), player);
                    case ROSTER -> values.put(node.id(), livingSet(MentaloutControlContext.subjects(player)));
                    case INTRUSION_TARGET -> values.put(node.id(), MentalIntrusionManager.target(player));
                    case LOOK_TARGET -> {
                        var target = MentaloutTargeting.findPrecisionLookedAtLiving(player);
                        if (target == null) {
                            return Evaluation.error(PrecisionGraph.Diagnostic.NO_SIGHT_TARGET, node.id());
                        }
                        values.put(node.id(), target);
                    }
                    case SIGHT_POSITION -> {
                        var observer = requireEntity(input(program, values, node, 0));
                        var destination = MentaloutTargeting.findSightDestination(
                                observer, MentaloutTargeting.MAX_SIGHT_RANGE);
                        if (destination == null) {
                            return Evaluation.error(PrecisionGraph.Diagnostic.NO_SIGHT_TARGET, node.id());
                        }
                        values.put(node.id(), destination);
                    }
                    case NEARBY_ENTITIES -> values.put(node.id(), nearby(player, node.parameter()));
                    case PLAYER_TARGET -> {
                        var target = player.getLastHurtMob();
                        values.put(node.id(), target != null && target.isAlive() && !target.isRemoved()
                                && target.level() == player.level() ? target : null);
                    }
                    case CURRENT_TARGET -> {
                        var subject = entity(input(program, values, node, 0));
                        values.put(node.id(), subject == null ? null : effectiveTarget(subject));
                    }
                    case LAST_ATTACKER -> {
                        var subject = entity(input(program, values, node, 0));
                        var attacker = subject == null ? null : subject.getLastHurtByMob();
                        values.put(node.id(), attacker != null && attacker.isAlive() && !attacker.isRemoved()
                                && attacker.level() == player.level() ? attacker : null);
                    }
                    case ENTITY_TO_SET -> {
                        var selected = entity(input(program, values, node, 0));
                        values.put(node.id(), selected == null ? List.of() : livingSet(List.of(selected)));
                    }
                    case UNION -> values.put(node.id(), union(
                            entitySet(input(program, values, node, 0)),
                            entitySet(input(program, values, node, 1))
                    ));
                    case INTERSECTION -> {
                        var right = new HashSet<>(entitySet(input(program, values, node, 1)));
                        values.put(node.id(), entitySet(input(program, values, node, 0)).stream()
                                .filter(right::contains).toList());
                    }
                    case SUBTRACT_SET -> {
                        var right = new HashSet<>(entitySet(input(program, values, node, 1)));
                        values.put(node.id(), entitySet(input(program, values, node, 0)).stream()
                                .filter(entity -> !right.contains(entity)).toList());
                    }
                    case ALIVE -> values.put(node.id(), entitySet(input(program, values, node, 0)).stream()
                            .filter(LivingEntity::isAlive).filter(entity -> !entity.isRemoved()).toList());
                    case DISTANCE -> values.put(node.id(), entitySet(input(program, values, node, 0)).stream()
                            .filter(entity -> entity.level() == player.level())
                            .filter(entity -> entity.distanceToSqr(player) <= node.parameter() * node.parameter())
                            .toList());
                    case ALLIES -> values.put(node.id(), entitySet(input(program, values, node, 0)).stream()
                            .filter(entity -> isAlly(player, entity)).toList());
                    case ENEMIES -> values.put(node.id(), entitySet(input(program, values, node, 0)).stream()
                            .filter(entity -> entity != player && !isAlly(player, entity)).toList());
                    case ABILITY_SUPPORTED -> {
                        var capability = ControlCapability.values()[(int) node.parameter()];
                        values.put(node.id(), entitySet(input(program, values, node, 0)).stream()
                                .filter(entity -> MentalControlRuntime.evaluate(entity, capability).supported())
                                .toList());
                    }
                    case EXCLUDE -> {
                        var excluded = entity(input(program, values, node, 1));
                        values.put(node.id(), entitySet(input(program, values, node, 0)).stream()
                                .filter(entity -> entity != excluded).toList());
                    }
                    case NEAREST -> values.put(node.id(), entitySet(input(program, values, node, 0)).stream()
                            .min(distanceComparator(player)).orElse(null));
                    case FARTHEST -> values.put(node.id(), entitySet(input(program, values, node, 0)).stream()
                            .min(farthestComparator(player)).orElse(null));
                    case LOWEST_HEALTH -> values.put(node.id(), entitySet(input(program, values, node, 0)).stream()
                            .min(healthComparator(player)).orElse(null));
                    case HIGHEST_HEALTH -> values.put(node.id(), entitySet(input(program, values, node, 0)).stream()
                            .min(highestHealthComparator(player)).orElse(null));
                    case LIMIT -> values.put(node.id(), entitySet(input(program, values, node, 0)).stream()
                            .limit((int) node.parameter()).toList());
                    case TARGETED_BY -> {
                        var target = requireEntity(input(program, values, node, 1));
                        values.put(node.id(), entitySet(input(program, values, node, 0)).stream()
                                .filter(entity -> {
                                    var current = effectiveTarget(entity);
                                    return current != null && current.getUUID().equals(target.getUUID());
                                })
                                .toList());
                    }
                    case HOSTILE_TO -> {
                        var target = requireEntity(input(program, values, node, 1));
                        values.put(node.id(), entitySet(input(program, values, node, 0)).stream()
                                .filter(entity -> isHostileTo(entity, target))
                                .toList());
                    }
                    case LAST_DAMAGED_BY -> {
                        var target = requireEntity(input(program, values, node, 1));
                        values.put(node.id(), entitySet(input(program, values, node, 0)).stream()
                                .filter(entity -> entity.getLastHurtByMob() == target)
                                .toList());
                    }
                    case SORT_BY_DISTANCE -> {
                        var ascending = node.parameter() == 0.0;
                        var sorted = entitySet(input(program, values, node, 0)).stream()
                                .sorted(ascending ? distanceComparator(player) : farthestComparator(player))
                                .toList();
                        values.put(node.id(), sorted);
                    }
                    case RANDOM -> {
                        var entities = entitySet(input(program, values, node, 0));
                        values.put(node.id(), entities.isEmpty()
                                ? null
                                : entities.get(player.getRandom().nextInt(entities.size())));
                    }
                    case TYPE_FILTER -> values.put(node.id(), entitySet(input(program, values, node, 0)).stream()
                            .filter(entity -> typeMatches((int) node.parameter(), entity))
                            .toList());
                    case HEALTH_FILTER -> values.put(node.id(), entitySet(input(program, values, node, 0)).stream()
                            .filter(entity -> entity.getMaxHealth() > 0.0f
                                    && entity.getHealth() / entity.getMaxHealth() * 100.0 >= node.parameter())
                            .toList());
                    case HEALTH_BELOW -> values.put(node.id(), entitySet(input(program, values, node, 0)).stream()
                            .filter(entity -> entity.getMaxHealth() > 0.0f
                                    && entity.getHealth() / entity.getMaxHealth() * 100.0 <= node.parameter())
                            .toList());
                    case HAS_TARGET -> values.put(node.id(), entitySet(input(program, values, node, 0)).stream()
                            .filter(entity -> effectiveTarget(entity) != null)
                            .toList());
                    case AFFECTED -> values.put(node.id(), entitySet(input(program, values, node, 0)).stream()
                            .filter(MentalControlApi::hasActiveControl)
                            .toList());
                    case VISIBLE_FROM -> {
                        var observer = requireEntity(input(program, values, node, 1));
                        values.put(node.id(), entitySet(input(program, values, node, 0)).stream()
                                .filter(entity -> entity.level() == observer.level())
                                .filter(observer::hasLineOfSight)
                                .toList());
                    }
                    case TARGET_MISIDENTIFICATION -> {
                        var subjects = supportedSet(
                                input(program, values, node, 0), ControlCapability.FORCE_TARGET, player);
                        if (subjects.isEmpty()) {
                            return Evaluation.error(PrecisionGraph.Diagnostic.UNSUPPORTED_TARGET, node.id());
                        }
                        var target = requireEntity(input(program, values, node, 1));
                        requireSkill(Skills.TARGET_MISIDENTIFICATION.get(), player);
                        cost += MentaloutConfig.targetMisidentificationCost(player) * subjects.size();
                        addTargets(uniqueTargets, subjects, target);
                        actions.add(PendingAction.withBoth(
                                node.id(), node.kind(), subjects, target, actionExpiresAt(now, node.parameter())));
                        values.put(node.id(), Boolean.TRUE);
                    }
                    case MENTAL_STUPOR -> {
                        var subjects = supportedSet(
                                input(program, values, node, 0), ControlCapability.FREEZE_AI, player);
                        if (subjects.isEmpty()) {
                            return Evaluation.error(PrecisionGraph.Diagnostic.UNSUPPORTED_TARGET, node.id());
                        }
                        requireSkill(Skills.MENTAL_STUPOR.get(), player);
                        cost += subjects.stream().mapToDouble(entity -> controlledCost(
                                player, entity, MentaloutConfig.mentalStuporCost(player))).sum();
                        addTargets(uniqueTargets, subjects, (LivingEntity) null);
                        actions.add(PendingAction.withSet(
                                node.id(), node.kind(), subjects, actionExpiresAt(now, node.parameter())));
                        values.put(node.id(), Boolean.TRUE);
                    }
                    case IMPRESSION_MANIPULATION -> {
                        var subjects = supportedSet(
                                input(program, values, node, 0), ControlCapability.RELATION_CONTROL, player);
                        if (subjects.isEmpty()) {
                            return Evaluation.error(PrecisionGraph.Diagnostic.UNSUPPORTED_TARGET, node.id());
                        }
                        requireSkill(Skills.IMPRESSION_MANIPULATION.get(), player);
                        cost += subjects.stream().mapToDouble(entity -> controlledCost(
                                player, entity, MentaloutConfig.impressionManipulationCost(player))).sum();
                        addTargets(uniqueTargets, subjects, (LivingEntity) null);
                        actions.add(PendingAction.withSet(
                                node.id(), node.kind(), subjects, actionExpiresAt(now, node.parameter())));
                        values.put(node.id(), Boolean.TRUE);
                    }
                    case PERCEPTION_MASK -> {
                        var observers = requireSet(input(program, values, node, 0));
                        var hidden = requireEntity(input(program, values, node, 1));
                        requireSkill(Skills.SENSORY_DISTORTION.get(), player);
                        var sensoryLevel = Math.clamp(Skills.SENSORY_DISTORTION.get().getLevel(player), 0, 2);
                        cost += MentaloutConfig.sensoryDistortionCost(player, sensoryLevel) * observers.size();
                        addTargets(uniqueTargets, observers, hidden);
                        actions.add(PendingAction.withBoth(
                                node.id(), node.kind(), observers, hidden, actionExpiresAt(now, node.parameter())));
                        values.put(node.id(), Boolean.TRUE);
                    }
                    case START_INTRUSION -> {
                        var target = requireEntity(input(program, values, node, 0));
                        requireSkill(Skills.MENTAL_INTRUSION.get(), player);
                        var intrusionLevel = Math.clamp(Skills.MENTAL_INTRUSION.get().getLevel(player), 0, 2);
                        cost += MentaloutConfig.mentalIntrusionCost(player, intrusionLevel);
                        addTargets(uniqueTargets, List.of(), target);
                        actions.add(PendingAction.withEntity(
                                node.id(), node.kind(), target, actionExpiresAt(now, node.parameter())));
                        values.put(node.id(), Boolean.TRUE);
                    }
                    case PATH_TO -> {
                        var subjects = supportedSet(
                                input(program, values, node, 0), ControlCapability.PATH_CONTROL, player);
                        if (subjects.isEmpty()) {
                            return Evaluation.error(PrecisionGraph.Diagnostic.UNSUPPORTED_TARGET, node.id());
                        }
                        var destination = requireDestination(input(program, values, node, 1));
                        subjects = excludeDestinationTarget(subjects, destination);
                        if (subjects.isEmpty()) {
                            return Evaluation.error(PrecisionGraph.Diagnostic.NO_EFFECTIVE_SUBJECTS, node.id());
                        }
                        cost += subjects.stream().mapToDouble(entity -> controlledCost(
                                player, entity, MentaloutConfig.precisionPathCost(player))).sum();
                        addTargets(uniqueTargets, subjects, destination);
                        actions.add(PendingAction.withDestination(
                                node.id(), node.kind(), subjects, destination, actionExpiresAt(now, node.parameter())));
                        values.put(node.id(), Boolean.TRUE);
                    }
                    case VIEW_CONTROL -> {
                        var subjects = supportedSet(
                                input(program, values, node, 0), ControlCapability.VIEW_CONTROL, player);
                        if (subjects.isEmpty()) {
                            return Evaluation.error(PrecisionGraph.Diagnostic.UNSUPPORTED_TARGET, node.id());
                        }
                        var target = requireEntity(input(program, values, node, 1));
                        subjects = subjects.stream().filter(subject -> subject != target).toList();
                        if (subjects.isEmpty()) {
                            return Evaluation.error(PrecisionGraph.Diagnostic.NO_EFFECTIVE_SUBJECTS, node.id());
                        }
                        cost += subjects.stream().mapToDouble(entity -> controlledCost(
                                player, entity, MentaloutConfig.precisionViewCost(player))).sum();
                        addTargets(uniqueTargets, subjects, target);
                        actions.add(PendingAction.withBoth(
                                node.id(), node.kind(), subjects, target, actionExpiresAt(now, node.parameter())));
                        values.put(node.id(), Boolean.TRUE);
                    }
                    case GUARD_MODE -> {
                        var subjects = supportedSet(
                                input(program, values, node, 0), ControlCapability.GUARD_CONTROL, player);
                        if (subjects.isEmpty()) {
                            return Evaluation.error(PrecisionGraph.Diagnostic.UNSUPPORTED_TARGET, node.id());
                        }
                        var destination = requireDestination(input(program, values, node, 1));
                        subjects = excludeDestinationTarget(subjects, destination);
                        if (subjects.isEmpty()) {
                            return Evaluation.error(PrecisionGraph.Diagnostic.NO_EFFECTIVE_SUBJECTS, node.id());
                        }
                        cost += subjects.stream().mapToDouble(entity -> controlledCost(
                                player, entity, MentaloutConfig.precisionGuardCost(player))).sum();
                        addTargets(uniqueTargets, subjects, destination);
                        actions.add(PendingAction.withDestination(
                                node.id(), node.kind(), subjects, destination, actionExpiresAt(now, node.parameter())));
                        values.put(node.id(), Boolean.TRUE);
                    }
                    case REMOVE_CONTROL -> {
                        var subjects = usableSet(input(program, values, node, 0), player);
                        if (subjects.isEmpty()) {
                            return Evaluation.error(PrecisionGraph.Diagnostic.UNSUPPORTED_TARGET, node.id());
                        }
                        actions.add(PendingAction.withSet(node.id(), node.kind(), subjects, now + 1L));
                        values.put(node.id(), Boolean.TRUE);
                    }
                    case END_INTRUSION -> {
                        endIntrusion = true;
                        actions.add(PendingAction.empty(node.id(), node.kind(), now + 1L));
                        values.put(node.id(), Boolean.TRUE);
                    }
                }
            } catch (RuntimeException exception) {
                return Evaluation.error(PrecisionGraph.Diagnostic.ACTION_FAILED, node.id());
            }
            if (uniqueTargets.size() > targetLimit) {
                return Evaluation.error(PrecisionGraph.Diagnostic.TARGET_LIMIT, node.id());
            }
        }
        if (actions.isEmpty()) return Evaluation.error(PrecisionGraph.Diagnostic.EMPTY_PROGRAM);
        if (!Float.isFinite(cost) || cost < 0.0f) return Evaluation.error(PrecisionGraph.Diagnostic.ACTION_FAILED);
        return new Evaluation(
                actions,
                cost,
                endIntrusion,
                Set.copyOf(uniqueTargets),
                PrecisionGraph.Diagnostic.OK,
                -1,
                -1,
                0
        );
    }

    private static Object input(
            CompiledPrecisionProgram program,
            Map<Integer, Object> values,
            PrecisionGraph.Node node,
            int port
    ) {
        var edge = program.input(node.id(), port);
        if (edge == null) throw new IllegalStateException("Missing compiled input");
        return values.get(edge.fromNode());
    }

    private static List<LivingEntity> nearby(ServerPlayer player, double range) {
        return livingSet(player.level().getEntitiesOfClass(
                LivingEntity.class,
                new AABB(player.position(), player.position()).inflate(range),
                entity -> entity != player && entity.isAlive() && !entity.isRemoved()
        ));
    }

    private static List<LivingEntity> livingSet(List<? extends LivingEntity> entities) {
        return List.copyOf(new LinkedHashSet<>(entities));
    }

    private static List<LivingEntity> union(
            List<? extends LivingEntity> left,
            List<? extends LivingEntity> right
    ) {
        var result = new LinkedHashSet<LivingEntity>();
        result.addAll(left);
        result.addAll(right);
        return List.copyOf(result);
    }

    private static Comparator<LivingEntity> distanceComparator(ServerPlayer player) {
        return Comparator.comparingDouble((LivingEntity entity) -> entity.distanceToSqr(player))
                .thenComparing(LivingEntity::getUUID);
    }

    private static Comparator<LivingEntity> farthestComparator(ServerPlayer player) {
        return Comparator.comparingDouble((LivingEntity entity) -> -entity.distanceToSqr(player))
                .thenComparing(LivingEntity::getUUID);
    }

    private static Comparator<LivingEntity> healthComparator(ServerPlayer player) {
        return Comparator.comparingDouble(PrecisionOperationRuntime::healthRatio)
                .thenComparingDouble(entity -> entity.distanceToSqr(player))
                .thenComparing(LivingEntity::getUUID);
    }

    private static Comparator<LivingEntity> highestHealthComparator(ServerPlayer player) {
        return Comparator.comparingDouble((LivingEntity entity) -> -healthRatio(entity))
                .thenComparingDouble(entity -> entity.distanceToSqr(player))
                .thenComparing(LivingEntity::getUUID);
    }

    private static double healthRatio(LivingEntity entity) {
        return entity.getMaxHealth() <= 0.0f ? 0.0 : entity.getHealth() / entity.getMaxHealth();
    }

    @SuppressWarnings("unchecked")
    private static List<LivingEntity> entitySet(Object value) {
        return value instanceof List<?> list && list.stream().allMatch(LivingEntity.class::isInstance)
                ? (List<LivingEntity>) list
                : List.of();
    }

    private static LivingEntity entity(Object value) {
        return value instanceof LivingEntity living ? living : null;
    }

    private static ControlDestination requireDestination(Object value) {
        if (value instanceof LivingEntity living) {
            requireUsable(living);
            return new ControlDestination.Entity(living.getUUID());
        }
        if (value instanceof ControlDestination destination) return destination;
        throw new IllegalStateException("Unavailable destination");
    }

    static long actionExpiresAt(long now, double seconds) {
        return seconds == 0.0 ? Long.MAX_VALUE : now + (long) seconds * 20L;
    }

    private static List<LivingEntity> requireSet(Object value) {
        var entities = entitySet(value);
        if (entities.isEmpty()) throw new IllegalStateException("Empty entity set");
        for (var entity : entities) requireUsable(entity);
        return entities;
    }

    private static LivingEntity requireEntity(Object value) {
        var entity = entity(value);
        requireUsable(entity);
        return entity;
    }

    private static void requireUsable(LivingEntity entity) {
        if (entity == null || !entity.isAlive() || entity.isRemoved()) {
            throw new IllegalStateException("Unavailable entity");
        }
    }

    private static void requireSkill(org.academy.api.common.ability.Skill skill, ServerPlayer player) {
        if (!skill.isEnabled(player)) throw new IllegalStateException("Required skill unavailable");
    }

    private static boolean isAlly(ServerPlayer player, LivingEntity entity) {
        return entity == player || player.isAlliedTo(entity) || FriendlyFireSetting.shouldPrevent(player, entity);
    }

    private static double controlledCost(ServerPlayer player, LivingEntity entity, float base) {
        return base * (MentalControlRuntime.isBossCost(entity)
                ? MentaloutConfig.bossCostMultiplier(player)
                : 1.0f);
    }

    private static void addTargets(Set<UUID> targetIds, List<LivingEntity> entities, LivingEntity entity) {
        entities.forEach(target -> targetIds.add(target.getUUID()));
        if (entity != null) targetIds.add(entity.getUUID());
    }

    private static void addTargets(
            Set<UUID> targetIds,
            List<LivingEntity> entities,
            ControlDestination destination
    ) {
        entities.forEach(target -> targetIds.add(target.getUUID()));
        if (destination instanceof ControlDestination.Entity entity) targetIds.add(entity.uuid());
    }

    private static List<LivingEntity> usableSet(Object value, ServerPlayer player) {
        return entitySet(value).stream()
                .filter(LivingEntity::isAlive)
                .filter(entity -> !entity.isRemoved())
                .filter(entity -> entity.level() == player.level())
                .toList();
    }

    private static List<LivingEntity> supportedSet(
            Object value,
            ControlCapability capability,
            ServerPlayer player
    ) {
        return usableSet(value, player).stream()
                .filter(entity -> !MentalControlRuntime.isProtectedTarget(entity))
                .filter(entity -> MentalControlRuntime.evaluate(entity, capability).supported())
                .toList();
    }

    private static List<LivingEntity> excludeDestinationTarget(
            List<LivingEntity> subjects,
            ControlDestination destination
    ) {
        if (!(destination instanceof ControlDestination.Entity entity)) return subjects;
        return subjects.stream().filter(subject -> !subject.getUUID().equals(entity.uuid())).toList();
    }

    private static LivingEntity effectiveTarget(LivingEntity entity) {
        if (entity instanceof Mob mob) {
            var forced = MentalControlRuntime.getForcedTarget(mob);
            return forced != null ? forced : mob.getTarget();
        }
        return null;
    }

    private static boolean isHostileTo(LivingEntity entity, LivingEntity target) {
        var decision = MentalControlApi.attackDecision(entity, target);
        if (decision == AttackDecision.ALLOW) return true;
        if (decision == AttackDecision.DENY) return false;
        return entity instanceof Mob mob && mob.getTarget() == target
                || entity.getLastHurtByMob() == target;
    }

    private static boolean typeMatches(int type, LivingEntity entity) {
        return switch (type) {
            case 0 -> entity instanceof Monster;
            case 1 -> entity instanceof Animal;
            case 2 -> entity instanceof ServerPlayer;
            case 3 -> MentalControlRuntime.isBossCost(entity);
            default -> false;
        };
    }

    private static Set<UUID> removedSubjects(List<PendingAction> actions) {
        var result = new HashSet<UUID>();
        for (var action : actions) {
            if (action.kind == PrecisionGraph.NodeKind.REMOVE_CONTROL) {
                action.entities.forEach(entity -> result.add(entity.getUUID()));
            }
        }
        return result;
    }

    private static void releaseSubjects(ServerPlayer player, Set<UUID> subjects) {
        if (subjects.isEmpty()) return;
        var slots = ACTIVE.get(player.getUUID());
        if (slots != null) {
            for (var context : slots) {
                if (context != null) context.releaseSubjects(subjects);
            }
        }
        if (slots == null) return;
        var remaining = activeCost(slots);
        var system = AbilitySystemServer.getSystem(player);
        if (remaining <= 0.0f) {
            system.releaseMaintenanceOccupation(
                    player.getUUID(),
                    Skills.PRECISION_OPERATION.get().getKeyString()
            );
        } else {
            system.replacePermanentOccupation(
                    player.getUUID(),
                    remaining,
                    Skills.PRECISION_OPERATION.get()
            );
        }
    }

    private static float subjectCost(ActiveContext[] slots, Set<UUID> subjects) {
        if (subjects.isEmpty()) return 0.0f;
        var result = 0.0f;
        for (var context : slots) {
            if (context != null) result += context.subjectCost(subjects);
        }
        return result;
    }

    private static float subjectCostExceptSlot(
            ActiveContext[] slots,
            int excludedSlot,
            Set<UUID> subjects
    ) {
        if (subjects.isEmpty()) return 0.0f;
        var result = 0.0f;
        for (var slot = 0; slot < slots.length; slot++) {
            if (slot == excludedSlot || slots[slot] == null) continue;
            result += slots[slot].subjectCost(subjects);
        }
        return result;
    }

    private static float actionCost(ServerPlayer player, LivingEntity subject, PrecisionGraph.NodeKind kind) {
        return switch (kind) {
            case TARGET_MISIDENTIFICATION -> MentaloutConfig.targetMisidentificationCost(player);
            case MENTAL_STUPOR -> (float) controlledCost(player, subject, MentaloutConfig.mentalStuporCost(player));
            case IMPRESSION_MANIPULATION -> (float) controlledCost(
                    player, subject, MentaloutConfig.impressionManipulationCost(player));
            case PATH_TO -> (float) controlledCost(player, subject, MentaloutConfig.precisionPathCost(player));
            case VIEW_CONTROL -> (float) controlledCost(player, subject, MentaloutConfig.precisionViewCost(player));
            case GUARD_MODE -> (float) controlledCost(player, subject, MentaloutConfig.precisionGuardCost(player));
            default -> 0.0f;
        };
    }

    private static void applyDirective(
            ServerPlayer player,
            List<LivingEntity> subjects,
            ControlDirective directive,
            PrecisionGraph.NodeKind kind,
            long expiresAt,
            Map<UUID, List<AutoCloseable>> subjectHandles,
            Map<UUID, Float> subjectCosts
    ) {
        for (var subject : subjects) {
            if (MentalControlRuntime.isProtectedTarget(subject)) {
                throw new IllegalStateException("Protected target");
            }
            var handle = MentalControlApi.apply(new ControlRequest(
                    player,
                    subject,
                    Skills.PRECISION_OPERATION.get().getKey(),
                    PRIORITY,
                    expiresAt,
                    List.of(directive)
            ));
            subjectHandles.computeIfAbsent(subject.getUUID(), _ -> new ArrayList<>()).add(handle);
            subjectCosts.merge(subject.getUUID(), actionCost(player, subject, kind), Float::sum);
        }
    }

    private static void applyMisidentification(
            ServerPlayer player,
            List<LivingEntity> subjects,
            LivingEntity target,
            long expiresAt,
            Map<UUID, List<AutoCloseable>> subjectHandles,
            Map<UUID, Float> subjectCosts
    ) {
        for (var subject : subjects) {
            if (subject == target) continue;
            applyDirective(
                    player,
                    List.of(subject),
                    new ControlDirective.ForceTarget(target.getUUID()),
                    PrecisionGraph.NodeKind.TARGET_MISIDENTIFICATION,
                    expiresAt,
                    subjectHandles,
                    subjectCosts
            );
        }
    }

    private static void applyPerception(
            ServerPlayer player,
            List<LivingEntity> observers,
            LivingEntity hidden,
            long expiresAt,
            Map<UUID, List<AutoCloseable>> subjectHandles,
            Map<UUID, Float> subjectCosts
    ) {
        var sensoryLevel = Math.clamp(Skills.SENSORY_DISTORTION.get().getLevel(player), 0, 2);
        var cost = MentaloutConfig.sensoryDistortionCost(player, sensoryLevel);
        for (var observer : observers) {
            if (MentalControlRuntime.isProtectedTarget(observer)) {
                throw new IllegalStateException("Protected target");
            }
            var handle = MentalPerceptionRuntime.apply(
                    player,
                    observer,
                    hidden,
                    Skills.PRECISION_OPERATION.get().getKey(),
                    PRIORITY,
                    expiresAt
            );
            subjectHandles.computeIfAbsent(observer.getUUID(), _ -> new ArrayList<>()).add(handle);
            subjectCosts.merge(observer.getUUID(), cost, Float::sum);
        }
    }
    private static void stop(ServerPlayer player, int slot) {
        var slots = ACTIVE.get(player.getUUID());
        if (slots == null || slot < 0 || slot >= slots.length) return;
        var context = slots[slot];
        if (context == null) return;
        slots[slot] = null;
        context.close(player);
        if (activeCost(slots) <= 0.0f) {
            AbilitySystemServer.getSystem(player).releaseMaintenanceOccupation(
                    player.getUUID(),
                    Skills.PRECISION_OPERATION.get().getKeyString()
            );
        } else {
            AbilitySystemServer.getSystem(player).replacePermanentOccupation(
                    player.getUUID(),
                    activeCost(slots),
                    Skills.PRECISION_OPERATION.get()
            );
        }
        if (java.util.Arrays.stream(slots).allMatch(java.util.Objects::isNull)) {
            ACTIVE.remove(player.getUUID());
        }
    }

    private static void releaseController(UUID controllerId, ServerPlayer knownPlayer) {
        var slots = ACTIVE.remove(controllerId);
        if (slots == null) return;
        for (var context : slots) {
            if (context != null) context.close(knownPlayer);
        }
        if (knownPlayer != null) {
            AbilitySystemServer.getSystem(knownPlayer).releaseMaintenanceOccupation(
                    controllerId,
                    Skills.PRECISION_OPERATION.get().getKeyString()
            );
        }
    }

    private static float activeCost(ActiveContext[] slots) {
        var result = 0.0f;
        for (var context : slots) if (context != null) result += context.cost();
        return result;
    }

    private static void closeReverse(List<? extends AutoCloseable> handles) {
        for (var i = handles.size() - 1; i >= 0; i--) {
            try {
                handles.get(i).close();
            } catch (Exception ignored) {
            }
        }
    }

    public enum ExecutionState {
        STARTED,
        CANCELLED,
        COMPLETED,
        FAILED
    }

    public record ExecutionResult(
            ExecutionState state,
            PrecisionGraph.Diagnostic diagnostic,
            int nodeId,
            int port,
            int affectedCount
    ) {
        private static ExecutionResult failed(PrecisionGraph.Diagnostic diagnostic) {
            return failed(diagnostic, -1, -1, 0);
        }

        private static ExecutionResult failed(
                PrecisionGraph.Diagnostic diagnostic,
                int nodeId,
                int port,
                int affectedCount
        ) {
            return new ExecutionResult(ExecutionState.FAILED, diagnostic, nodeId, port, affectedCount);
        }

        private static ExecutionResult started() {
            return new ExecutionResult(ExecutionState.STARTED, PrecisionGraph.Diagnostic.OK, -1, -1, 0);
        }

        private static ExecutionResult cancelled() {
            return new ExecutionResult(ExecutionState.CANCELLED, PrecisionGraph.Diagnostic.OK, -1, -1, 0);
        }

        private static ExecutionResult completed() {
            return new ExecutionResult(ExecutionState.COMPLETED, PrecisionGraph.Diagnostic.OK, -1, -1, 0);
        }
    }

    private record Evaluation(
            List<PendingAction> actions,
            float cost,
            boolean endIntrusion,
            Set<UUID> targetIds,
            PrecisionGraph.Diagnostic diagnostic,
            int nodeId,
            int port,
            int affectedCount
    ) {
        private static Evaluation error(PrecisionGraph.Diagnostic diagnostic) {
            return error(diagnostic, -1, -1, 0);
        }

        private static Evaluation error(PrecisionGraph.Diagnostic diagnostic, int nodeId) {
            return error(diagnostic, nodeId, -1, 0);
        }

        private static Evaluation error(
                PrecisionGraph.Diagnostic diagnostic,
                int nodeId,
                int port,
                int affectedCount
        ) {
            return new Evaluation(List.of(), 0.0f, false, Set.of(), diagnostic,
                    nodeId, port, affectedCount);
        }

        private boolean valid() {
            return diagnostic == PrecisionGraph.Diagnostic.OK;
        }
    }

    private record PendingAction(
            int nodeId,
            PrecisionGraph.NodeKind kind,
            List<LivingEntity> entities,
            LivingEntity entity,
            ControlDestination destination,
            long expiresAt
    ) {
        private static PendingAction empty(int nodeId, PrecisionGraph.NodeKind kind, long expiresAt) {
            return new PendingAction(nodeId, kind, List.of(), null, null, expiresAt);
        }

        private static PendingAction withSet(
                int nodeId,
                PrecisionGraph.NodeKind kind,
                List<LivingEntity> entities,
                long expiresAt
        ) {
            return new PendingAction(nodeId, kind, List.copyOf(entities), null, null, expiresAt);
        }

        private static PendingAction withEntity(
                int nodeId,
                PrecisionGraph.NodeKind kind,
                LivingEntity entity,
                long expiresAt
        ) {
            return new PendingAction(nodeId, kind, List.of(), entity, null, expiresAt);
        }

        private static PendingAction withBoth(
                int nodeId,
                PrecisionGraph.NodeKind kind,
                List<LivingEntity> entities,
                LivingEntity entity,
                long expiresAt
        ) {
            return new PendingAction(nodeId, kind, List.copyOf(entities), entity, null, expiresAt);
        }

        private static PendingAction withDestination(
                int nodeId,
                PrecisionGraph.NodeKind kind,
                List<LivingEntity> entities,
                ControlDestination destination,
                long expiresAt
        ) {
            return new PendingAction(nodeId, kind, List.copyOf(entities), null, destination, expiresAt);
        }

        private Set<UUID> dependencyIds() {
            var result = new HashSet<UUID>();
            if (entity != null) result.add(entity.getUUID());
            if (destination instanceof ControlDestination.Entity target) result.add(target.uuid());
            return Set.copyOf(result);
        }
    }

    private static final class ActiveContext {
        private final List<ActiveAction> actions;

        private ActiveContext(List<ActiveAction> actions) {
            this.actions = new ArrayList<>(actions);
        }

        private float cost() {
            var total = 0.0f;
            for (var action : actions) total += action.cost();
            return total;
        }

        private void close(ServerPlayer player) {
            actions.forEach(action -> action.close(player));
            actions.clear();
        }

        private float releaseSubjects(Set<UUID> subjects) {
            var released = 0.0f;
            for (var action : actions) released += action.releaseSubjects(subjects);
            actions.removeIf(ActiveAction::empty);
            return released;
        }

        private float subjectCost(Set<UUID> subjects) {
            var result = 0.0f;
            for (var action : actions) result += action.subjectCost(subjects);
            return result;
        }

        private boolean releaseEntity(ServerPlayer player, UUID entityId) {
            var changed = false;
            var iterator = actions.iterator();
            while (iterator.hasNext()) {
                var action = iterator.next();
                if (!action.releaseEntity(player, entityId)) continue;
                changed = true;
                if (action.empty()) iterator.remove();
            }
            return changed;
        }

        private ContextTick tick(ServerPlayer player, long now) {
            var changed = false;
            var failures = new HashMap<FailureKey, Integer>();
            var iterator = actions.iterator();
            while (iterator.hasNext()) {
                var action = iterator.next();
                var tick = action.tick(player, now);
                changed |= tick.changed();
                for (var failure : tick.failures()) {
                    failures.merge(
                            new FailureKey(failure.diagnostic(), failure.nodeId()),
                            failure.affectedCount(),
                            Integer::sum
                    );
                }
                if (action.empty()) iterator.remove();
            }
            return new ContextTick(changed, failures.entrySet().stream()
                    .map(entry -> new ActionFailure(
                            entry.getKey().diagnostic(),
                            entry.getKey().nodeId(),
                            entry.getValue()
                    )).toList());
        }

        private boolean empty() {
            return actions.isEmpty();
        }
    }

    private static final class ActiveAction {
        private final int nodeId;
        private final PrecisionGraph.NodeKind kind;
        private final long expiresAt;
        private final Map<UUID, List<AutoCloseable>> subjectHandles;
        private final Map<UUID, Float> subjectCosts;
        private final Set<UUID> targetIds;
        private float fixedCost;
        private UUID intrusionSession;

        private ActiveAction(
                int nodeId,
                PrecisionGraph.NodeKind kind,
                long expiresAt,
                Map<UUID, List<AutoCloseable>> subjectHandles,
                Map<UUID, Float> subjectCosts,
                float fixedCost,
                UUID intrusionSession,
                Set<UUID> targetIds
        ) {
            this.nodeId = nodeId;
            this.kind = kind;
            this.expiresAt = expiresAt;
            this.subjectHandles = subjectHandles;
            this.subjectCosts = subjectCosts;
            this.fixedCost = fixedCost;
            this.intrusionSession = intrusionSession;
            this.targetIds = targetIds;
        }

        private float cost() {
            var total = fixedCost;
            for (var cost : subjectCosts.values()) total += cost;
            return total;
        }

        private ActionTick tick(ServerPlayer player, long now) {
            if (now >= expiresAt) {
                close(player);
                return new ActionTick(true, List.of());
            }
            var changed = false;
            var failures = new HashMap<PrecisionGraph.Diagnostic, Integer>();
            var iterator = subjectHandles.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                if (!entry.getValue().stream().allMatch(PrecisionOperationRuntime::isHandleClosed)) continue;
                var diagnostic = entry.getValue().stream()
                        .filter(ControlHandle.class::isInstance)
                        .map(ControlHandle.class::cast)
                        .map(ControlHandle::failureReason)
                        .flatMap(java.util.Optional::stream)
                        .findFirst()
                        .map(PrecisionOperationRuntime::diagnostic)
                        .orElse(null);
                if (diagnostic != null) failures.merge(diagnostic, 1, Integer::sum);
                iterator.remove();
                subjectCosts.remove(entry.getKey());
                changed = true;
            }
            if (intrusionSession != null
                    && !MentalIntrusionManager.isPrecisionActive(player, intrusionSession)) {
                intrusionSession = null;
                fixedCost = 0.0f;
                changed = true;
            }
            return new ActionTick(changed, failures.entrySet().stream()
                    .map(entry -> new ActionFailure(entry.getKey(), nodeId, entry.getValue()))
                    .toList());
        }

        private void close(ServerPlayer player) {
            subjectHandles.values().forEach(PrecisionOperationRuntime::closeReverse);
            subjectHandles.clear();
            subjectCosts.clear();
            fixedCost = 0.0f;
            if (player != null && intrusionSession != null) {
                MentalIntrusionManager.stopPrecision(player, intrusionSession);
            }
            intrusionSession = null;
        }

        private float releaseSubjects(Set<UUID> subjects) {
            var released = 0.0f;
            for (var subjectId : subjects) {
                var handles = subjectHandles.remove(subjectId);
                if (handles != null) closeReverse(handles);
                var cost = subjectCosts.remove(subjectId);
                if (cost != null) released += cost;
            }
            return released;
        }

        private float subjectCost(Set<UUID> subjects) {
            var result = 0.0f;
            for (var subject : subjects) result += subjectCosts.getOrDefault(subject, 0.0f);
            return result;
        }

        private boolean releaseEntity(ServerPlayer player, UUID entityId) {
            if (targetIds.contains(entityId)) {
                close(player);
                return true;
            }
            var handles = subjectHandles.remove(entityId);
            if (handles == null) return false;
            closeReverse(handles);
            subjectCosts.remove(entityId);
            return true;
        }

        private boolean empty() {
            return subjectHandles.isEmpty() && fixedCost <= 0.0f && intrusionSession == null;
        }
    }

    private static boolean isHandleClosed(AutoCloseable handle) {
        if (handle instanceof ControlHandle control) return control.isClosed();
        if (handle instanceof MentalPerceptionRuntime.Handle perception) return perception.isClosed();
        return false;
    }

    private static PrecisionGraph.Diagnostic diagnostic(ControlFailureReason reason) {
        return switch (reason) {
            case UNREACHABLE_DESTINATION -> PrecisionGraph.Diagnostic.UNREACHABLE_DESTINATION;
            case TARGET_UNAVAILABLE -> PrecisionGraph.Diagnostic.TARGET_UNAVAILABLE;
            case ADAPTER_ERROR -> PrecisionGraph.Diagnostic.ADAPTER_ERROR;
        };
    }

    private record FailureKey(PrecisionGraph.Diagnostic diagnostic, int nodeId) {
    }

    private record ActionFailure(
            PrecisionGraph.Diagnostic diagnostic,
            int nodeId,
            int affectedCount
    ) {
    }

    private record ActionTick(boolean changed, List<ActionFailure> failures) {
    }

    private record ContextTick(boolean changed, List<ActionFailure> failures) {
    }
}
