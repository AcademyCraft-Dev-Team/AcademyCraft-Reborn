package org.academy.internal.common.ability.mentalout.precision;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.api.common.entitycontrol.AttackDecision;
import org.academy.api.common.entitycontrol.ControlCapability;
import org.academy.api.common.entitycontrol.ControlApplyException;
import org.academy.api.common.entitycontrol.ControlDestination;
import org.academy.api.common.entitycontrol.ControlDirective;
import org.academy.api.common.entitycontrol.ControlFailureReason;
import org.academy.api.common.entitycontrol.ControlHandle;
import org.academy.api.common.entitycontrol.ControlRequest;
import org.academy.api.common.entitycontrol.MentalControlApi;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.common.ability.SkillProficiencyProfile;
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
    private static long nextContextSequence;

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
        var targetLimit = actionSubjectLimit(level);
        var evaluated = evaluate(player, program, targetLimit, player.level().getGameTime());
        if (!evaluated.valid()) {
            return ExecutionResult.failed(
                    evaluated.diagnostic, evaluated.nodeId, evaluated.port, evaluated.affectedCount);
        }

        var removedSubjects = removedSubjects(evaluated.actions);
        var occupationPlan = projectedOccupationPlan(
                player, slots, slot, evaluated, removedSubjects, player.level().getGameTime());
        var system = AbilitySystemServer.getSystem(player);
        if (!system.canReplacePermanentOccupationAndAddTimedOccupations(
                player.getUUID(),
                skill,
                occupationPlan.permanentCost(),
                occupationPlan.timedCharges()
        )) {
            return ExecutionResult.failed(PrecisionGraph.Diagnostic.INSUFFICIENT_CP);
        }

        var activeActions = new ArrayList<ActiveAction>();
        var applyingNodeId = -1;
        PrecisionGraph.NodeKind applyingKind = null;
        try {
            // A repeated cast refreshes this slot. Every action has its own completion condition,
            // so clicking the skill is never interpreted as a manual off switch.
            slots[slot] = null;
            if (previous != null) previous.close(player);
            for (var action : evaluated.actions) {
                applyingNodeId = action.nodeId;
                applyingKind = action.kind;
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
                        fixedCost += fixedActionCost(player, action.kind);
                    }
                    case END_INTRUSION, REMOVE_CONTROL -> {
                    }
                    case HEALTH_RATIO_BRANCH, DISTANCE_BRANCH, ENTITY_TYPE_BRANCH,
                         STATUS_EFFECT_BRANCH -> {
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
            slots[slot] = activeActions.isEmpty()
                    ? null : new ActiveContext(++nextContextSequence, activeActions);
            if (!system.replacePermanentOccupationAndAddTimedOccupations(
                    player.getUUID(), skill, occupationPlan.permanentCost(), occupationPlan.timedCharges())) {
                throw new IllegalStateException("CP occupation rejected");
            }
            if (evaluated.endIntrusion) MentalIntrusionManager.stopAny(player);
            if (java.util.Arrays.stream(slots).allMatch(java.util.Objects::isNull)) {
                ACTIVE.remove(player.getUUID());
            }
            return activeActions.isEmpty() ? ExecutionResult.completed() : ExecutionResult.started();
        } catch (RuntimeException exception) {
            activeActions.forEach(action -> action.close(player));
            system.replacePermanentOccupation(player.getUUID(), permanentCost(player, slots), skill);
            var diagnostic = actionDiagnostic(exception);
            if (diagnostic == PrecisionGraph.Diagnostic.ACTION_FAILED) {
                AcademyCraft.LOGGER.error(
                        "Precision Operation action node {} ({}) failed and was rolled back",
                        applyingNodeId,
                        applyingKind,
                        exception
                );
            } else {
                AcademyCraft.LOGGER.debug(
                        "Precision Operation action node {} ({}) was rejected as {}",
                        applyingNodeId,
                        applyingKind,
                        diagnostic,
                        exception
                );
            }
            return ExecutionResult.failed(diagnostic, applyingNodeId, -1, 0);
        }
    }

    static int actionSubjectLimit(int level) {
        return switch (Math.clamp(level, 0, 2)) {
            case 0 -> 16;
            case 1 -> 32;
            default -> 64;
        };
    }

    public static void tick(MinecraftServer server) {
        var now = server.overworld().getGameTime();
        for (var entry : List.copyOf(ACTIVE.entrySet())) {
            var player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !player.isAlive() || !Skills.PRECISION_OPERATION.get().isEnabled(player)) {
                releaseController(entry.getKey(), player);
                continue;
            }
            Skills.PRECISION_OPERATION.get().reportActivity(player, true);
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
                var remaining = permanentCost(player, entry.getValue());
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
                var remaining = permanentCost(player, entry.getValue());
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
        var uniqueSubjects = new HashSet<UUID>();
        var roster = livingSet(MentaloutControlContext.subjects(player));
        var rosterIds = roster.stream().map(LivingEntity::getUUID).collect(java.util.stream.Collectors.toSet());
        var reachableActions = new HashSet<Integer>();
        if (!program.actionOrder().isEmpty()) {
            reachableActions.add(program.actionOrder().getFirst().id());
        }
        var cost = 0.0f;
        var endIntrusion = false;
        for (var node : program.order()) {
            if (node.kind().isAction() && !reachableActions.contains(node.id())) continue;
            try {
                switch (node.kind()) {
                    case CASTER -> values.put(node.id(), player);
                    case ROSTER -> values.put(node.id(), roster);
                    case INTRUSION_TARGET -> values.put(node.id(), MentalIntrusionManager.target(player));
                    case LOOK_TARGET -> {
                        var target = MentaloutTargeting.findPrecisionLookedAtLiving(player);
                        if (target == null) {
                            return Evaluation.error(PrecisionGraph.Diagnostic.NO_SIGHT_TARGET, node.id());
                        }
                        values.put(node.id(), target);
                    }
                    case SIGHT_POSITION -> {
                        var observer = requireLivingEntity(input(program, values, node, 0));
                        var destination = MentaloutTargeting.findSightDestination(
                                observer, MentaloutTargeting.MAX_SIGHT_RANGE);
                        if (destination == null) {
                            return Evaluation.error(PrecisionGraph.Diagnostic.NO_SIGHT_TARGET, node.id());
                        }
                        values.put(node.id(), destination);
                    }
                    case ENTITY_POSITION -> {
                        var target = requireEntity(input(program, values, node, 0));
                        var position = resolvedPosition(target, player);
                        values.put(node.id(), new ControlDestination.Position(
                                position.dimension(), position.value()));
                    }
                    case DIRECTION_BETWEEN -> {
                        var origin = resolvedPosition(input(program, values, node, 0), player);
                        var target = resolvedPosition(input(program, values, node, 1), player);
                        requireSameDimension(origin, target);
                        var delta = target.value().subtract(origin.value());
                        if (delta.lengthSqr() <= 1.0e-8) {
                            throw new EvaluationFailure(PrecisionGraph.Diagnostic.INVALID_DIRECTION);
                        }
                        values.put(node.id(), delta.normalize());
                    }
                    case POSITION_OFFSET -> {
                        var origin = resolvedPosition(input(program, values, node, 0), player);
                        var direction = requireDirection(input(program, values, node, 1));
                        values.put(node.id(), new ControlDestination.Position(
                                origin.dimension(), origin.value().add(direction.scale(node.parameter()))));
                    }
                    case NEARBY_ENTITIES -> values.put(node.id(), nearbyLiving(player, node.parameter()));
                    case NEARBY_ALL_ENTITIES -> values.put(node.id(), nearbyEntities(player, node.parameter()));
                    case NEARBY_ITEMS -> values.put(node.id(), nearbyEntities(player, node.parameter()).stream()
                            .filter(ItemEntity.class::isInstance).toList());
                    case NEARBY_PROJECTILES -> values.put(node.id(), nearbyEntities(player, node.parameter()).stream()
                            .filter(Projectile.class::isInstance).toList());
                    case PLAYER_TARGET -> {
                        var target = player.getLastHurtMob();
                        values.put(node.id(), target != null && target.isAlive() && !target.isRemoved()
                                && target.level() == player.level() ? target : null);
                    }
                    case CURRENT_TARGET -> {
                        var subject = entity(input(program, values, node, 0));
                        values.put(node.id(), subject instanceof LivingEntity living
                                ? effectiveTarget(living) : null);
                    }
                    case LAST_ATTACKER -> {
                        var subject = entity(input(program, values, node, 0));
                        var attacker = subject instanceof LivingEntity living ? living.getLastHurtByMob() : null;
                        values.put(node.id(), attacker != null && attacker.isAlive() && !attacker.isRemoved()
                                && attacker.level() == player.level() ? attacker : null);
                    }
                    case ENTITY_TO_SET -> {
                        var selected = entity(input(program, values, node, 0));
                        values.put(node.id(), selected == null ? List.of() : entitySet(List.of(selected)));
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
                            .filter(Entity::isAlive).filter(entity -> !entity.isRemoved()).toList());
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
                                .filter(LivingEntity.class::isInstance)
                                .filter(entity -> MentalControlRuntime.evaluate(
                                        (LivingEntity) entity, capability).supported())
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
                            .filter(LivingEntity.class::isInstance)
                            .min(healthComparator(player)).orElse(null));
                    case HIGHEST_HEALTH -> values.put(node.id(), entitySet(input(program, values, node, 0)).stream()
                            .filter(LivingEntity.class::isInstance)
                            .min(highestHealthComparator(player)).orElse(null));
                    case LIMIT -> values.put(node.id(), entitySet(input(program, values, node, 0)).stream()
                            .limit((int) node.parameter()).toList());
                    case TARGETED_BY -> {
                        var target = requireLivingEntity(input(program, values, node, 1));
                        values.put(node.id(), entitySet(input(program, values, node, 0)).stream()
                                .filter(entity -> {
                                    var current = entity instanceof LivingEntity living
                                            ? effectiveTarget(living) : null;
                                    return current != null && current.getUUID().equals(target.getUUID());
                                })
                                .toList());
                    }
                    case HOSTILE_TO -> {
                        var target = requireLivingEntity(input(program, values, node, 1));
                        values.put(node.id(), entitySet(input(program, values, node, 0)).stream()
                                .filter(entity -> entity instanceof LivingEntity living
                                        && isHostileTo(living, target))
                                .toList());
                    }
                    case LAST_DAMAGED_BY -> {
                        var target = requireLivingEntity(input(program, values, node, 1));
                        values.put(node.id(), entitySet(input(program, values, node, 0)).stream()
                                .filter(entity -> entity instanceof LivingEntity living
                                        && living.getLastHurtByMob() == target)
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
                            .filter(entity -> entity instanceof LivingEntity living
                                    && living.getMaxHealth() > 0.0f
                                    && living.getHealth() / living.getMaxHealth() * 100.0 >= node.parameter())
                            .toList());
                    case HEALTH_BELOW -> values.put(node.id(), entitySet(input(program, values, node, 0)).stream()
                            .filter(entity -> entity instanceof LivingEntity living
                                    && living.getMaxHealth() > 0.0f
                                    && living.getHealth() / living.getMaxHealth() * 100.0 <= node.parameter())
                            .toList());
                    case HAS_TARGET -> values.put(node.id(), entitySet(input(program, values, node, 0)).stream()
                            .filter(entity -> entity instanceof LivingEntity living
                                    && effectiveTarget(living) != null)
                            .toList());
                    case AFFECTED -> values.put(node.id(), entitySet(input(program, values, node, 0)).stream()
                            .filter(LivingEntity.class::isInstance)
                            .filter(entity -> rosterIds.contains(entity.getUUID())
                                    || MentalControlApi.hasActiveControl((LivingEntity) entity))
                            .toList());
                    case VISIBLE_FROM -> {
                        var observer = requireLivingEntity(input(program, values, node, 1));
                        values.put(node.id(), entitySet(input(program, values, node, 0)).stream()
                                .filter(entity -> entity.level() == observer.level())
                                .filter(observer::hasLineOfSight)
                                .toList());
                    }
                    case HEALTH_RATIO_BRANCH -> {
                        var subject = requireLivingEntity(input(program, values, node, 0));
                        values.put(node.id(), subject.getMaxHealth() > 0.0f
                                && subject.getHealth() / subject.getMaxHealth() * 100.0 <= node.parameter());
                    }
                    case DISTANCE_BRANCH -> {
                        var subject = requireEntity(input(program, values, node, 0));
                        values.put(node.id(), subject.level() == player.level()
                                && subject.distanceToSqr(player) <= node.parameter() * node.parameter());
                    }
                    case ENTITY_TYPE_BRANCH -> {
                        var subject = requireEntity(input(program, values, node, 0));
                        values.put(node.id(), typeMatches((int) node.parameter(), subject));
                    }
                    case STATUS_EFFECT_BRANCH -> {
                        var subject = requireLivingEntity(input(program, values, node, 0));
                        values.put(node.id(), !subject.getActiveEffects().isEmpty());
                    }
                    case TARGET_MISIDENTIFICATION -> {
                        var subjects = requireSupportedSet(
                                input(program, values, node, 0), ControlCapability.FORCE_TARGET, player);
                        var target = requireLivingEntity(input(program, values, node, 1));
                        subjects = subjects.stream().filter(subject -> subject != target).toList();
                        if (subjects.isEmpty()) {
                            return Evaluation.error(PrecisionGraph.Diagnostic.NO_EFFECTIVE_SUBJECTS, node.id());
                        }
                        requireSkill(Skills.TARGET_MISIDENTIFICATION.get(), player);
                        cost += MentaloutConfig.precisionMisidentificationCost(player) * subjects.size();
                        addSubjects(uniqueSubjects, subjects);
                        actions.add(PendingAction.withBoth(
                                node.id(), node.kind(), subjects, target, actionExpiresAt(now, node.parameter())));
                        values.put(node.id(), Boolean.TRUE);
                    }
                    case MENTAL_STUPOR -> {
                        var subjects = requireSupportedSet(
                                input(program, values, node, 0), ControlCapability.FREEZE_AI, player);
                        requireSkill(Skills.MENTAL_STUPOR.get(), player);
                        cost += subjects.stream().mapToDouble(entity -> controlledCost(
                                player, entity, MentaloutConfig.precisionStuporCost(player))).sum();
                        addSubjects(uniqueSubjects, subjects);
                        actions.add(PendingAction.withSet(
                                node.id(), node.kind(), subjects, actionExpiresAt(now, node.parameter())));
                        values.put(node.id(), Boolean.TRUE);
                    }
                    case IMPRESSION_MANIPULATION -> {
                        var subjects = requireSupportedSet(
                                input(program, values, node, 0), ControlCapability.RELATION_CONTROL, player);
                        requireSkill(Skills.IMPRESSION_MANIPULATION.get(), player);
                        cost += subjects.stream().mapToDouble(entity -> controlledCost(
                                player, entity, MentaloutConfig.precisionImpressionCost(player))).sum();
                        addSubjects(uniqueSubjects, subjects);
                        actions.add(PendingAction.withSet(
                                node.id(), node.kind(), subjects, actionExpiresAt(now, node.parameter())));
                        values.put(node.id(), Boolean.TRUE);
                    }
                    case PERCEPTION_MASK -> {
                        var observers = requireSet(input(program, values, node, 0));
                        var hidden = requireLivingEntity(input(program, values, node, 1));
                        ensureUnprotected(player, observers);
                        requireSkill(Skills.SENSORY_DISTORTION.get(), player);
                        var sensoryLevel = Math.clamp(Skills.SENSORY_DISTORTION.get().getLevel(player), 0, 2);
                        cost += MentaloutConfig.precisionSensoryCost(player, sensoryLevel) * observers.size();
                        addSubjects(uniqueSubjects, observers);
                        actions.add(PendingAction.withBoth(
                                node.id(), node.kind(), observers, hidden, actionExpiresAt(now, node.parameter())));
                        values.put(node.id(), Boolean.TRUE);
                    }
                    case START_INTRUSION -> {
                        var target = requireLivingEntity(input(program, values, node, 0));
                        ensureUnprotected(player, List.of(target));
                        requireSkill(Skills.MENTAL_INTRUSION.get(), player);
                        var intrusionLevel = Math.clamp(Skills.MENTAL_INTRUSION.get().getLevel(player), 0, 2);
                        cost += MentaloutConfig.precisionIntrusionCost(player, intrusionLevel);
                        addSubjects(uniqueSubjects, List.of(target));
                        actions.add(PendingAction.withEntity(
                                node.id(), node.kind(), target, actionExpiresAt(now, node.parameter())));
                        values.put(node.id(), Boolean.TRUE);
                    }
                    case PATH_TO -> {
                        var subjects = requireSupportedSet(
                                input(program, values, node, 0), ControlCapability.PATH_CONTROL, player);
                        var destination = requireDestination(input(program, values, node, 1));
                        subjects = excludeDestinationTarget(subjects, destination);
                        if (subjects.isEmpty()) {
                            return Evaluation.error(PrecisionGraph.Diagnostic.NO_EFFECTIVE_SUBJECTS, node.id());
                        }
                        cost += subjects.stream().mapToDouble(entity -> controlledCost(
                                player, entity, MentaloutConfig.precisionPathCost(player))).sum();
                        addSubjects(uniqueSubjects, subjects);
                        actions.add(PendingAction.withDestination(
                                node.id(), node.kind(), subjects, destination, actionExpiresAt(now, node.parameter())));
                        values.put(node.id(), Boolean.TRUE);
                    }
                    case VIEW_CONTROL -> {
                        var subjects = requireSupportedSet(
                                input(program, values, node, 0), ControlCapability.VIEW_CONTROL, player);
                        var target = requireLivingEntity(input(program, values, node, 1));
                        subjects = subjects.stream().filter(subject -> subject != target).toList();
                        if (subjects.isEmpty()) {
                            return Evaluation.error(PrecisionGraph.Diagnostic.NO_EFFECTIVE_SUBJECTS, node.id());
                        }
                        cost += subjects.stream().mapToDouble(entity -> controlledCost(
                                player, entity, MentaloutConfig.precisionViewCost(player))).sum();
                        addSubjects(uniqueSubjects, subjects);
                        actions.add(PendingAction.withBoth(
                                node.id(), node.kind(), subjects, target, actionExpiresAt(now, node.parameter())));
                        values.put(node.id(), Boolean.TRUE);
                    }
                    case GUARD_MODE -> {
                        var subjects = requireSupportedSet(
                                input(program, values, node, 0), ControlCapability.GUARD_CONTROL, player);
                        var destination = requireDestination(input(program, values, node, 1));
                        subjects = excludeDestinationTarget(subjects, destination);
                        if (subjects.isEmpty()) {
                            return Evaluation.error(PrecisionGraph.Diagnostic.NO_EFFECTIVE_SUBJECTS, node.id());
                        }
                        cost += subjects.stream().mapToDouble(entity -> controlledCost(
                                player, entity, MentaloutConfig.precisionGuardCost(player))).sum();
                        addSubjects(uniqueSubjects, subjects);
                        actions.add(PendingAction.withDestination(
                                node.id(), node.kind(), subjects, destination, actionExpiresAt(now, node.parameter())));
                        values.put(node.id(), Boolean.TRUE);
                    }
                    case REMOVE_CONTROL -> {
                        var subjects = usableSet(input(program, values, node, 0), player);
                        if (subjects.isEmpty()) {
                            return Evaluation.error(PrecisionGraph.Diagnostic.NO_EFFECTIVE_SUBJECTS, node.id());
                        }
                        addSubjects(uniqueSubjects, subjects);
                        actions.add(PendingAction.withSet(node.id(), node.kind(), subjects, now + 1L));
                        values.put(node.id(), Boolean.TRUE);
                    }
                    case END_INTRUSION -> {
                        endIntrusion = true;
                        actions.add(PendingAction.empty(node.id(), node.kind(), now + 1L));
                        values.put(node.id(), Boolean.TRUE);
                    }
                }
                if (node.kind().isAction()) {
                    var outputPort = node.kind().isConditionalBranch()
                            && Boolean.FALSE.equals(values.get(node.id())) ? 1 : 0;
                    var next = program.flowTarget(node.id(), outputPort);
                    if (next != null) reachableActions.add(next);
                }
            } catch (ProtectedTargetException exception) {
                return Evaluation.error(PrecisionGraph.Diagnostic.PROTECTED_TARGET, node.id());
            } catch (EvaluationFailure exception) {
                return Evaluation.error(exception.diagnostic, node.id());
            } catch (RuntimeException exception) {
                AcademyCraft.LOGGER.error(
                        "Precision Operation failed while evaluating node {} ({})",
                        node.id(),
                        node.kind(),
                        exception
                );
                return Evaluation.error(PrecisionGraph.Diagnostic.ACTION_FAILED, node.id());
            }
            if (uniqueSubjects.size() > targetLimit) {
                return Evaluation.error(PrecisionGraph.Diagnostic.TARGET_LIMIT, node.id());
            }
        }
        if (actions.isEmpty()) return Evaluation.error(PrecisionGraph.Diagnostic.EMPTY_PROGRAM);
        if (!Float.isFinite(cost) || cost < 0.0f) return Evaluation.error(PrecisionGraph.Diagnostic.ACTION_FAILED);
        return new Evaluation(
                actions,
                cost,
                endIntrusion,
                Set.copyOf(uniqueSubjects),
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

    private static List<Entity> nearbyLiving(ServerPlayer player, double range) {
        return entitySet(player.level().getEntitiesOfClass(
                LivingEntity.class,
                new AABB(player.position(), player.position()).inflate(range),
                entity -> entity != player && entity.isAlive() && !entity.isRemoved()
        ));
    }

    private static List<Entity> nearbyEntities(ServerPlayer player, double range) {
        return entitySet(player.level().getEntities(
                player,
                new AABB(player.position(), player.position()).inflate(range),
                entity -> entity.isAlive() && !entity.isRemoved()
        ));
    }

    private static List<LivingEntity> livingSet(List<? extends LivingEntity> entities) {
        return List.copyOf(new LinkedHashSet<>(entities));
    }

    private static List<Entity> entitySet(List<? extends Entity> entities) {
        return List.copyOf(new LinkedHashSet<>(entities));
    }

    private static List<Entity> union(
            List<? extends Entity> left,
            List<? extends Entity> right
    ) {
        var result = new LinkedHashSet<Entity>();
        result.addAll(left);
        result.addAll(right);
        return List.copyOf(result);
    }

    private static Comparator<Entity> distanceComparator(ServerPlayer player) {
        return Comparator.comparingDouble((Entity entity) -> entity.distanceToSqr(player))
                .thenComparing(Entity::getUUID);
    }

    private static Comparator<Entity> farthestComparator(ServerPlayer player) {
        return Comparator.comparingDouble((Entity entity) -> -entity.distanceToSqr(player))
                .thenComparing(Entity::getUUID);
    }

    private static Comparator<Entity> healthComparator(ServerPlayer player) {
        return Comparator.comparingDouble((Entity entity) -> healthRatio((LivingEntity) entity))
                .thenComparingDouble(entity -> entity.distanceToSqr(player))
                .thenComparing(Entity::getUUID);
    }

    private static Comparator<Entity> highestHealthComparator(ServerPlayer player) {
        return Comparator.comparingDouble((Entity entity) -> -healthRatio((LivingEntity) entity))
                .thenComparingDouble(entity -> entity.distanceToSqr(player))
                .thenComparing(Entity::getUUID);
    }

    private static double healthRatio(LivingEntity entity) {
        return entity.getMaxHealth() <= 0.0f ? 0.0 : entity.getHealth() / entity.getMaxHealth();
    }

    @SuppressWarnings("unchecked")
    private static List<Entity> entitySet(Object value) {
        return value instanceof List<?> list && list.stream().allMatch(Entity.class::isInstance)
                ? (List<Entity>) list
                : List.of();
    }

    private static Entity entity(Object value) {
        return value instanceof Entity entity ? entity : null;
    }

    private static ControlDestination requireDestination(Object value) {
        if (value instanceof LivingEntity living) {
            requireUsable(living);
            return new ControlDestination.Entity(living.getUUID());
        }
        if (value instanceof Entity entity) {
            requireUsable(entity);
            return new ControlDestination.Position(
                    entity.level().dimension().identifier(), entity.position());
        }
        if (value instanceof ControlDestination destination) return destination;
        throw new EvaluationFailure(PrecisionGraph.Diagnostic.NO_EFFECTIVE_TARGET);
    }

    private static ResolvedPosition resolvedPosition(Object value, ServerPlayer player) {
        if (value instanceof Entity entity) {
            requireUsable(entity);
            return new ResolvedPosition(entity.level().dimension().identifier(), entity.position());
        }
        if (value instanceof ControlDestination.Position position) {
            return new ResolvedPosition(position.dimension(), position.value());
        }
        if (value instanceof ControlDestination.Entity entity) {
            Entity target = null;
            for (var level : player.level().getServer().getAllLevels()) {
                target = level.getEntity(entity.uuid());
                if (target != null) break;
            }
            if (target == null) throw new EvaluationFailure(PrecisionGraph.Diagnostic.TARGET_UNAVAILABLE);
            return new ResolvedPosition(target.level().dimension().identifier(), target.position());
        }
        throw new EvaluationFailure(PrecisionGraph.Diagnostic.NO_EFFECTIVE_TARGET);
    }

    private static void requireSameDimension(ResolvedPosition origin, ResolvedPosition target) {
        if (!origin.dimension().equals(target.dimension())) {
            throw new EvaluationFailure(PrecisionGraph.Diagnostic.TARGET_UNAVAILABLE);
        }
    }

    private static Vec3 requireDirection(Object value) {
        if (!(value instanceof Vec3 direction) || direction.lengthSqr() <= 1.0e-8
                || !Double.isFinite(direction.x) || !Double.isFinite(direction.y)
                || !Double.isFinite(direction.z)) {
            throw new EvaluationFailure(PrecisionGraph.Diagnostic.INVALID_DIRECTION);
        }
        return direction.normalize();
    }

    static long actionExpiresAt(long now, double seconds) {
        return seconds == 0.0 ? Long.MAX_VALUE : now + (long) seconds * 20L;
    }

    private static List<LivingEntity> requireSet(Object value) {
        var entities = entitySet(value).stream()
                .filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast)
                .toList();
        if (entities.isEmpty()) {
            throw new EvaluationFailure(PrecisionGraph.Diagnostic.NO_EFFECTIVE_SUBJECTS);
        }
        for (var entity : entities) requireUsable(entity);
        return entities;
    }

    private static Entity requireEntity(Object value) {
        var entity = entity(value);
        if (entity == null) {
            throw new EvaluationFailure(PrecisionGraph.Diagnostic.NO_EFFECTIVE_TARGET);
        }
        requireUsable(entity);
        return entity;
    }

    private static LivingEntity requireLivingEntity(Object value) {
        var entity = entity(value);
        if (!(entity instanceof LivingEntity living)) {
            throw new EvaluationFailure(PrecisionGraph.Diagnostic.NO_EFFECTIVE_TARGET);
        }
        requireUsable(living);
        return living;
    }

    private static void requireUsable(Entity entity) {
        if (entity == null || !entity.isAlive() || entity.isRemoved()) {
            throw new EvaluationFailure(PrecisionGraph.Diagnostic.TARGET_UNAVAILABLE);
        }
    }

    private static void requireSkill(org.academy.api.common.ability.Skill skill, ServerPlayer player) {
        if (!skill.isEnabled(player)) {
            throw new EvaluationFailure(PrecisionGraph.Diagnostic.SKILL_UNAVAILABLE);
        }
    }

    private static boolean isAlly(ServerPlayer player, Entity entity) {
        return entity == player || player.isAlliedTo(entity)
                || entity instanceof LivingEntity living
                && FriendlyFireSetting.shouldPrevent(player, living);
    }

    private static double controlledCost(ServerPlayer player, LivingEntity entity, float base) {
        return base * (MentalControlRuntime.isBossCost(entity)
                ? MentaloutConfig.bossCostMultiplier(player)
                : 1.0f);
    }

    private static void addSubjects(Set<UUID> subjectIds, List<LivingEntity> entities) {
        entities.forEach(subject -> subjectIds.add(subject.getUUID()));
    }

    private static List<LivingEntity> usableSet(Object value, ServerPlayer player) {
        return entitySet(value).stream()
                .filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast)
                .filter(LivingEntity::isAlive)
                .filter(entity -> !entity.isRemoved())
                .filter(entity -> entity.level() == player.level())
                .toList();
    }

    private static List<LivingEntity> requireSupportedSet(
            Object value,
            ControlCapability capability,
            ServerPlayer player
    ) {
        var entities = usableSet(value, player);
        if (entities.isEmpty()) {
            throw new EvaluationFailure(PrecisionGraph.Diagnostic.NO_EFFECTIVE_SUBJECTS);
        }
        ensureUnprotected(player, entities, capability);
        var supported = entities.stream()
                .filter(entity -> entity == player
                        && (capability == ControlCapability.PATH_CONTROL
                        || capability == ControlCapability.VIEW_CONTROL)
                        || MentalControlRuntime.evaluate(entity, capability).supported())
                .toList();
        if (supported.isEmpty()) {
            throw new EvaluationFailure(PrecisionGraph.Diagnostic.UNSUPPORTED_TARGET);
        }
        return supported;
    }

    private static void ensureUnprotected(ServerPlayer player, List<LivingEntity> entities) {
        var protectedTarget = entities.stream()
                .filter(MentalControlRuntime::isProtectedTarget)
                .findFirst()
                .orElse(null);
        if (protectedTarget == null) return;
        MentalControlRuntime.notifyProtectionBlocked(player, protectedTarget);
        throw new ProtectedTargetException();
    }

    private static void ensureUnprotected(
            ServerPlayer player,
            List<LivingEntity> entities,
            ControlCapability capability
    ) {
        if (capability != ControlCapability.PATH_CONTROL
                && capability != ControlCapability.VIEW_CONTROL) {
            ensureUnprotected(player, entities);
            return;
        }
        ensureUnprotected(player, entities.stream().filter(entity -> entity != player).toList());
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

    private static boolean typeMatches(int type, Entity entity) {
        return switch (type) {
            case 0 -> entity instanceof Monster;
            case 1 -> entity instanceof Animal;
            case 2 -> entity instanceof ServerPlayer;
            case 3 -> entity instanceof LivingEntity living && MentalControlRuntime.isBossCost(living);
            case 4 -> entity instanceof Projectile;
            case 5 -> !(entity instanceof LivingEntity);
            case 6 -> entity instanceof LivingEntity;
            case 7 -> entity instanceof ItemEntity;
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
        if (slots != null) {
            var remaining = permanentCost(player, slots);
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
        MentaloutControlContext.releaseInterventionSubjects(player, subjects);
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
        var baseCost = switch (kind) {
            case TARGET_MISIDENTIFICATION -> MentaloutConfig.precisionMisidentificationCost(player);
            case MENTAL_STUPOR -> (float) controlledCost(player, subject, MentaloutConfig.precisionStuporCost(player));
            case IMPRESSION_MANIPULATION -> (float) controlledCost(
                    player, subject, MentaloutConfig.precisionImpressionCost(player));
            case PATH_TO -> (float) controlledCost(player, subject, MentaloutConfig.precisionPathCost(player));
            case VIEW_CONTROL -> (float) controlledCost(player, subject, MentaloutConfig.precisionViewCost(player));
            case GUARD_MODE -> (float) controlledCost(player, subject, MentaloutConfig.precisionGuardCost(player));
            case PERCEPTION_MASK -> MentaloutConfig.precisionSensoryCost(
                    player, Math.clamp(Skills.SENSORY_DISTORTION.get().getLevel(player), 0, 2));
            default -> 0.0f;
        };
        return Skills.PRECISION_OPERATION.get().adjustProficiencyCost(
                player, SkillProficiencyProfile.CostKind.DYNAMIC, baseCost);
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
            var selfMovementOrView = subject == player
                    && (kind == PrecisionGraph.NodeKind.PATH_TO
                    || kind == PrecisionGraph.NodeKind.VIEW_CONTROL);
            if (!selfMovementOrView && MentalControlRuntime.isProtectedTarget(subject)) {
                MentalControlRuntime.notifyProtectionBlocked(player, subject);
                throw new ProtectedTargetException();
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

    private static PrecisionGraph.Diagnostic actionDiagnostic(RuntimeException exception) {
        if (exception instanceof ProtectedTargetException) {
            return PrecisionGraph.Diagnostic.PROTECTED_TARGET;
        }
        if (exception instanceof ControlApplyException applyException) {
            return switch (applyException.reason()) {
                case IMMUNE_TAG, PROTECTED_PLAYER -> PrecisionGraph.Diagnostic.PROTECTED_TARGET;
                case NO_ADAPTER, UNSUPPORTED_CAPABILITY -> PrecisionGraph.Diagnostic.UNSUPPORTED_TARGET;
                case TEMPORARILY_UNAVAILABLE, AMBIGUOUS_ADAPTER, ADAPTER_ERROR ->
                        PrecisionGraph.Diagnostic.ADAPTER_ERROR;
                case INVALID_DIRECTIVE -> PrecisionGraph.Diagnostic.TARGET_UNAVAILABLE;
                case SUPPORTED -> PrecisionGraph.Diagnostic.ACTION_FAILED;
            };
        }
        if (exception instanceof IllegalStateException
                && "CP occupation rejected".equals(exception.getMessage())) {
            return PrecisionGraph.Diagnostic.INSUFFICIENT_CP;
        }
        return PrecisionGraph.Diagnostic.ACTION_FAILED;
    }

    private static final class EvaluationFailure extends RuntimeException {
        private final PrecisionGraph.Diagnostic diagnostic;

        private EvaluationFailure(PrecisionGraph.Diagnostic diagnostic) {
            this.diagnostic = diagnostic;
        }
    }

    private record ResolvedPosition(Identifier dimension, Vec3 value) {
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
        var cost = Skills.PRECISION_OPERATION.get().adjustProficiencyCost(
                player,
                SkillProficiencyProfile.CostKind.DYNAMIC,
                MentaloutConfig.precisionSensoryCost(player, sensoryLevel)
        );
        for (var observer : observers) {
            if (MentalControlRuntime.isProtectedTarget(observer)) {
                MentalControlRuntime.notifyProtectionBlocked(player, observer);
                throw new ProtectedTargetException();
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
        if (permanentCost(player, slots) <= 0.0f) {
            AbilitySystemServer.getSystem(player).releaseMaintenanceOccupation(
                    player.getUUID(),
                    Skills.PRECISION_OPERATION.get().getKeyString()
            );
        } else {
            AbilitySystemServer.getSystem(player).replacePermanentOccupation(
                    player.getUUID(),
                    permanentCost(player, slots),
                    Skills.PRECISION_OPERATION.get()
            );
        }
        if (java.util.Arrays.stream(slots).allMatch(java.util.Objects::isNull)) {
            ACTIVE.remove(player.getUUID());
        }
    }

    private static final class ProtectedTargetException extends RuntimeException {
        private ProtectedTargetException() {
            super(null, null, false, false);
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

    private static float permanentCost(ServerPlayer player, ActiveContext[] slots) {
        var share = Skills.PRECISION_OPERATION.get().hasProficiencyMilestone(player, 2);
        var shared = new HashSet<SharedCostKey>();
        var result = 0.0f;
        var ordered = java.util.Arrays.stream(slots)
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingLong(context -> context.sequence))
                .toList();
        for (var context : ordered) {
            for (var action : context.actions) {
                if (!action.isPermanent()) continue;
                result += action.fixedCost;
                for (var entry : action.subjectCosts.entrySet()) {
                    var key = new SharedCostKey(action.kind, entry.getKey());
                    if (!share || !isShareable(action.kind) || shared.add(key)) {
                        result += entry.getValue();
                    }
                }
            }
        }
        return result;
    }

    private static PrecisionOccupationPlan projectedOccupationPlan(
            ServerPlayer player,
            ActiveContext[] slots,
            int replacedSlot,
            Evaluation evaluated,
            Set<UUID> removedSubjects,
            long now
    ) {
        var share = Skills.PRECISION_OPERATION.get().hasProficiencyMilestone(player, 2);
        var sharedPermanent = new HashSet<SharedCostKey>();
        var sharedTimed = new HashSet<SharedCostKey>();
        var permanentCost = 0.0f;
        var timedCharges = new ArrayList<AbilitySystemServer.TimedOccupationCharge>();
        var ordered = java.util.stream.IntStream.range(0, slots.length)
                .filter(slot -> slot != replacedSlot && slots[slot] != null)
                .mapToObj(slot -> slots[slot])
                .sorted(Comparator.comparingLong(context -> context.sequence))
                .toList();
        for (var context : ordered) {
            for (var action : context.actions) {
                var permanent = action.isPermanent();
                var shared = permanent ? sharedPermanent : sharedTimed;
                if (permanent) permanentCost += action.fixedCost;
                for (var entry : action.subjectCosts.entrySet()) {
                    if (removedSubjects.contains(entry.getKey())) continue;
                    var key = new SharedCostKey(action.kind, entry.getKey());
                    if (!share || !isShareable(action.kind) || shared.add(key)) {
                        if (permanent) permanentCost += entry.getValue();
                    }
                }
            }
        }
        for (var action : evaluated.actions) {
            var permanent = action.expiresAt == Long.MAX_VALUE;
            var shared = permanent ? sharedPermanent : sharedTimed;
            var cost = fixedActionCost(player, action.kind);
            for (var subject : action.entities) {
                var key = new SharedCostKey(action.kind, subject.getUUID());
                if (!share || !isShareable(action.kind) || shared.add(key)) {
                    cost += actionCost(player, subject, action.kind);
                }
            }
            if (!(cost > 0.0f)) continue;
            if (permanent) {
                permanentCost += cost;
            } else {
                timedCharges.add(new AbilitySystemServer.TimedOccupationCharge(
                        cost, durationIterationPoints(now, action.expiresAt)));
            }
        }
        return new PrecisionOccupationPlan(
                Math.max(0.0f, permanentCost), List.copyOf(timedCharges));
    }

    private static float fixedActionCost(ServerPlayer player, PrecisionGraph.NodeKind kind) {
        if (kind != PrecisionGraph.NodeKind.START_INTRUSION) return 0.0f;
        var level = Math.clamp(Skills.MENTAL_INTRUSION.get().getLevel(player), 0, 2);
        return Skills.PRECISION_OPERATION.get().adjustProficiencyCost(
                player,
                SkillProficiencyProfile.CostKind.DYNAMIC,
                MentaloutConfig.precisionIntrusionCost(player, level)
        );
    }

    static int durationIterationPoints(long now, long expiresAt) {
        if (expiresAt == Long.MAX_VALUE) return 0;
        var durationTicks = Math.max(1L, expiresAt - now);
        return Math.clamp((int) Math.ceil(durationTicks / 40.0), 5, 20);
    }

    private static boolean isShareable(PrecisionGraph.NodeKind kind) {
        return kind == PrecisionGraph.NodeKind.PERCEPTION_MASK
                || kind == PrecisionGraph.NodeKind.PATH_TO
                || kind == PrecisionGraph.NodeKind.VIEW_CONTROL
                || kind == PrecisionGraph.NodeKind.GUARD_MODE;
    }

    private record SharedCostKey(PrecisionGraph.NodeKind kind, UUID subject) {
    }

    private record PrecisionOccupationPlan(
            float permanentCost,
            List<AbilitySystemServer.TimedOccupationCharge> timedCharges
    ) {
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
        private final long sequence;
        private final List<ActiveAction> actions;

        private ActiveContext(long sequence, List<ActiveAction> actions) {
            this.sequence = sequence;
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

        private boolean isPermanent() {
            return expiresAt == Long.MAX_VALUE;
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
            case CONTROL_RESISTANCE -> PrecisionGraph.Diagnostic.CONTROL_RESISTANCE;
            case UNSUPPORTED_MOVEMENT_MODE -> PrecisionGraph.Diagnostic.UNSUPPORTED_MOVEMENT_MODE;
            case PLANNING_BUDGET_EXHAUSTED -> PrecisionGraph.Diagnostic.PLANNING_BUDGET_EXHAUSTED;
            case CLIENT_TIMEOUT -> PrecisionGraph.Diagnostic.CLIENT_TIMEOUT;
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
