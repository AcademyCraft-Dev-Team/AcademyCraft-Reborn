package org.academy.internal.common.ability.mentalout.precision;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;
import net.minecraft.sounds.SoundSource;
import org.academy.api.common.entitycontrol.AttackDecision;
import org.academy.api.common.entitycontrol.ControlCapability;
import org.academy.api.common.entitycontrol.ControlDirective;
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

    public static ExecutionResult toggle(
            ServerPlayer player,
            int slot,
            CompiledPrecisionProgram program
    ) {
        if (player == null || slot < 0 || slot >= 4 || program == null) {
            return ExecutionResult.failed(PrecisionGraph.Diagnostic.ACTION_FAILED);
        }
        var slots = ACTIVE.computeIfAbsent(player.getUUID(), _ -> new ActiveContext[4]);
        if (slots[slot] != null) {
            stop(player, slot);
            return new ExecutionResult(true, false, PrecisionGraph.Diagnostic.OK);
        }
        var skill = Skills.PRECISION_OPERATION.get();
        if (!skill.isEnabled(player)) {
            return ExecutionResult.failed(PrecisionGraph.Diagnostic.SKILL_UNAVAILABLE);
        }
        var level = Math.clamp(skill.getLevel(player), 0, 2);
        var duration = switch (level) {
            case 0 -> 80;
            case 1 -> 140;
            default -> 200;
        };
        var targetLimit = switch (level) {
            case 0 -> 4;
            case 1 -> 6;
            default -> 8;
        };
        var evaluated = evaluate(player, program, targetLimit, player.level().getGameTime() + duration);
        if (!evaluated.valid()) return ExecutionResult.failed(evaluated.diagnostic);

        releaseSubjects(player, removedSubjects(evaluated.actions));
        var existingCost = activeCost(slots);
        var totalCost = existingCost + evaluated.cost;
        var system = AbilitySystemServer.getSystem(player);
        if (!system.canCastWithPermanentOccupations(
                player,
                skill,
                0.0f,
                Map.of(skill, totalCost)
        )) {
            return ExecutionResult.failed(PrecisionGraph.Diagnostic.INSUFFICIENT_CP);
        }

        var subjectHandles = new HashMap<UUID, List<AutoCloseable>>();
        var subjectCosts = new HashMap<UUID, Float>();
        var fixedCost = 0.0f;
        UUID intrusionSession = null;
        try {
            for (var action : evaluated.actions) {
                switch (action.kind) {
                    case TARGET_MISIDENTIFICATION -> applyMisidentification(
                            player, action.entities, action.entity, evaluated.expiresAt,
                            subjectHandles, subjectCosts);
                    case MENTAL_STUPOR -> applyDirective(
                            player, action.entities, new ControlDirective.FreezeAi(),
                            PrecisionGraph.NodeKind.MENTAL_STUPOR, evaluated.expiresAt,
                            subjectHandles, subjectCosts);
                    case IMPRESSION_MANIPULATION -> applyDirective(
                            player, action.entities, new ControlDirective.ImpressionAlliance(),
                            PrecisionGraph.NodeKind.IMPRESSION_MANIPULATION, evaluated.expiresAt,
                            subjectHandles, subjectCosts);
                    case PERCEPTION_MASK -> applyPerception(
                            player, action.entities, action.entity, evaluated.expiresAt,
                            subjectHandles, subjectCosts);
                    case START_INTRUSION -> {
                        intrusionSession = MentalIntrusionManager.startPrecision(
                                player,
                                action.entity,
                                evaluated.expiresAt
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
                            player, action.entities, new ControlDirective.MoveTo(action.entity.getUUID()),
                            PrecisionGraph.NodeKind.PATH_TO, evaluated.expiresAt,
                            subjectHandles, subjectCosts);
                    case VIEW_CONTROL -> applyDirective(
                            player, action.entities, new ControlDirective.LookAt(action.entity.getUUID()),
                            PrecisionGraph.NodeKind.VIEW_CONTROL, evaluated.expiresAt,
                            subjectHandles, subjectCosts);
                }
            }
            if (!system.replacePermanentOccupation(player.getUUID(), totalCost, skill)) {
                throw new IllegalStateException("CP occupation rejected");
            }
            slots[slot] = new ActiveContext(
                    evaluated.expiresAt,
                    Map.copyOf(subjectHandles),
                    Map.copyOf(subjectCosts),
                    fixedCost,
                    intrusionSession,
                    evaluated.targetIds
            );
            if (evaluated.endIntrusion) MentalIntrusionManager.stopAny(player);
            player.level().playSound(null, player.blockPosition(),
                    org.academy.internal.common.sounds.SoundEvents.PRECISION_OPERATION.get(),
                    SoundSource.PLAYERS, 0.75f, 1.0f);
            return new ExecutionResult(true, true, PrecisionGraph.Diagnostic.OK);
        } catch (RuntimeException exception) {
            closeReverse(subjectHandles.values().stream().flatMap(List::stream).toList());
            if (intrusionSession != null) MentalIntrusionManager.stopPrecision(player, intrusionSession);
            system.replacePermanentOccupation(player.getUUID(), existingCost, skill);
            return ExecutionResult.failed(PrecisionGraph.Diagnostic.ACTION_FAILED);
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
            for (var slot = 0; slot < entry.getValue().length; slot++) {
                var context = entry.getValue()[slot];
                if (context != null && now >= context.expiresAt) stop(player, slot);
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
            for (var slot = 0; slot < entry.getValue().length; slot++) {
                var context = entry.getValue()[slot];
                if (context != null && context.references(entityId)) stop(player, slot);
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
            long expiresAt
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
                    case LOOK_TARGET -> values.put(node.id(), MentaloutTargeting.findLookedAtLiving(player, 16.0));
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
                        var subjects = supportedSet(input(program, values, node, 0), ControlCapability.FORCE_TARGET);
                        if (subjects.isEmpty()) return Evaluation.error(PrecisionGraph.Diagnostic.UNSUPPORTED_TARGET);
                        var target = requireEntity(input(program, values, node, 1));
                        requireSkill(Skills.TARGET_MISIDENTIFICATION.get(), player);
                        cost += MentaloutConfig.targetMisidentificationCost(player) * subjects.size();
                        addTargets(uniqueTargets, subjects, target);
                        actions.add(PendingAction.withBoth(node.kind(), subjects, target));
                        values.put(node.id(), Boolean.TRUE);
                    }
                    case MENTAL_STUPOR -> {
                        var subjects = supportedSet(input(program, values, node, 0), ControlCapability.FREEZE_AI);
                        if (subjects.isEmpty()) return Evaluation.error(PrecisionGraph.Diagnostic.UNSUPPORTED_TARGET);
                        requireSkill(Skills.MENTAL_STUPOR.get(), player);
                        cost += subjects.stream().mapToDouble(entity -> controlledCost(
                                player, entity, MentaloutConfig.mentalStuporCost(player))).sum();
                        addTargets(uniqueTargets, subjects, null);
                        actions.add(PendingAction.withSet(node.kind(), subjects));
                        values.put(node.id(), Boolean.TRUE);
                    }
                    case IMPRESSION_MANIPULATION -> {
                        var subjects = supportedSet(input(program, values, node, 0), ControlCapability.RELATION_CONTROL);
                        if (subjects.isEmpty()) return Evaluation.error(PrecisionGraph.Diagnostic.UNSUPPORTED_TARGET);
                        requireSkill(Skills.IMPRESSION_MANIPULATION.get(), player);
                        cost += subjects.stream().mapToDouble(entity -> controlledCost(
                                player, entity, MentaloutConfig.impressionManipulationCost(player))).sum();
                        addTargets(uniqueTargets, subjects, null);
                        actions.add(PendingAction.withSet(node.kind(), subjects));
                        values.put(node.id(), Boolean.TRUE);
                    }
                    case PERCEPTION_MASK -> {
                        var observers = requireSet(input(program, values, node, 0));
                        var hidden = requireEntity(input(program, values, node, 1));
                        requireSkill(Skills.SENSORY_DISTORTION.get(), player);
                        var sensoryLevel = Math.clamp(Skills.SENSORY_DISTORTION.get().getLevel(player), 0, 2);
                        cost += MentaloutConfig.sensoryDistortionCost(player, sensoryLevel) * observers.size();
                        addTargets(uniqueTargets, observers, hidden);
                        actions.add(PendingAction.withBoth(node.kind(), observers, hidden));
                        values.put(node.id(), Boolean.TRUE);
                    }
                    case START_INTRUSION -> {
                        var target = requireEntity(input(program, values, node, 0));
                        requireSkill(Skills.MENTAL_INTRUSION.get(), player);
                        var intrusionLevel = Math.clamp(Skills.MENTAL_INTRUSION.get().getLevel(player), 0, 2);
                        cost += MentaloutConfig.mentalIntrusionCost(player, intrusionLevel);
                        addTargets(uniqueTargets, List.of(), target);
                        actions.add(PendingAction.withEntity(node.kind(), target));
                        values.put(node.id(), Boolean.TRUE);
                    }
                    case PATH_TO -> {
                        var subjects = supportedSet(input(program, values, node, 0), ControlCapability.PATH_CONTROL);
                        if (subjects.isEmpty()) return Evaluation.error(PrecisionGraph.Diagnostic.UNSUPPORTED_TARGET);
                        var target = requireEntity(input(program, values, node, 1));
                        cost += subjects.stream().mapToDouble(entity -> controlledCost(
                                player, entity, MentaloutConfig.precisionPathCost(player))).sum();
                        addTargets(uniqueTargets, subjects, target);
                        actions.add(PendingAction.withBoth(node.kind(), subjects, target));
                        values.put(node.id(), Boolean.TRUE);
                    }
                    case VIEW_CONTROL -> {
                        var subjects = supportedSet(input(program, values, node, 0), ControlCapability.VIEW_CONTROL);
                        if (subjects.isEmpty()) return Evaluation.error(PrecisionGraph.Diagnostic.UNSUPPORTED_TARGET);
                        var target = requireEntity(input(program, values, node, 1));
                        cost += subjects.stream().mapToDouble(entity -> controlledCost(
                                player, entity, MentaloutConfig.precisionViewCost(player))).sum();
                        addTargets(uniqueTargets, subjects, target);
                        actions.add(PendingAction.withBoth(node.kind(), subjects, target));
                        values.put(node.id(), Boolean.TRUE);
                    }
                    case REMOVE_CONTROL -> {
                        var subjects = usableSet(input(program, values, node, 0));
                        if (subjects.isEmpty()) return Evaluation.error(PrecisionGraph.Diagnostic.UNSUPPORTED_TARGET);
                        actions.add(PendingAction.withSet(node.kind(), subjects));
                        values.put(node.id(), Boolean.TRUE);
                    }
                    case END_INTRUSION -> {
                        endIntrusion = true;
                        actions.add(PendingAction.empty(node.kind()));
                        values.put(node.id(), Boolean.TRUE);
                    }
                }
            } catch (RuntimeException exception) {
                return Evaluation.error(PrecisionGraph.Diagnostic.ACTION_FAILED);
            }
            if (uniqueTargets.size() > targetLimit) {
                return Evaluation.error(PrecisionGraph.Diagnostic.TARGET_LIMIT);
            }
        }
        if (actions.isEmpty()) return Evaluation.error(PrecisionGraph.Diagnostic.EMPTY_PROGRAM);
        if (!Float.isFinite(cost) || cost < 0.0f) return Evaluation.error(PrecisionGraph.Diagnostic.ACTION_FAILED);
        return new Evaluation(
                actions,
                cost,
                expiresAt,
                endIntrusion,
                Set.copyOf(uniqueTargets),
                PrecisionGraph.Diagnostic.OK
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

    private static List<LivingEntity> usableSet(Object value) {
        return entitySet(value).stream()
                .filter(LivingEntity::isAlive)
                .filter(entity -> !entity.isRemoved())
                .toList();
    }

    private static List<LivingEntity> supportedSet(Object value, ControlCapability capability) {
        return usableSet(value).stream()
                .filter(entity -> !MentalControlRuntime.isProtectedTarget(entity))
                .filter(entity -> MentalControlRuntime.evaluate(entity, capability).supported())
                .toList();
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
        var source = Skills.PRECISION_OPERATION.get().getKey();
        for (var subjectId : subjects) {
            MentalControlApi.releaseByControllerSourceAndSubject(
                    player.level().getServer(),
                    player.getUUID(),
                    source,
                    subjectId
            );
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

    private static float actionCost(ServerPlayer player, LivingEntity subject, PrecisionGraph.NodeKind kind) {
        return switch (kind) {
            case TARGET_MISIDENTIFICATION -> MentaloutConfig.targetMisidentificationCost(player);
            case MENTAL_STUPOR -> (float) controlledCost(player, subject, MentaloutConfig.mentalStuporCost(player));
            case IMPRESSION_MANIPULATION -> (float) controlledCost(
                    player, subject, MentaloutConfig.impressionManipulationCost(player));
            case PATH_TO -> (float) controlledCost(player, subject, MentaloutConfig.precisionPathCost(player));
            case VIEW_CONTROL -> (float) controlledCost(player, subject, MentaloutConfig.precisionViewCost(player));
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

    public record ExecutionResult(boolean changed, boolean active, PrecisionGraph.Diagnostic diagnostic) {
        private static ExecutionResult failed(PrecisionGraph.Diagnostic diagnostic) {
            return new ExecutionResult(false, false, diagnostic);
        }
    }

    private record Evaluation(
            List<PendingAction> actions,
            float cost,
            long expiresAt,
            boolean endIntrusion,
            Set<UUID> targetIds,
            PrecisionGraph.Diagnostic diagnostic
    ) {
        private static Evaluation error(PrecisionGraph.Diagnostic diagnostic) {
            return new Evaluation(List.of(), 0.0f, 0L, false, Set.of(), diagnostic);
        }

        private boolean valid() {
            return diagnostic == PrecisionGraph.Diagnostic.OK;
        }
    }

    private record PendingAction(
            PrecisionGraph.NodeKind kind,
            List<LivingEntity> entities,
            LivingEntity entity
    ) {
        private static PendingAction empty(PrecisionGraph.NodeKind kind) {
            return new PendingAction(kind, List.of(), null);
        }

        private static PendingAction withSet(PrecisionGraph.NodeKind kind, List<LivingEntity> entities) {
            return new PendingAction(kind, List.copyOf(entities), null);
        }

        private static PendingAction withEntity(PrecisionGraph.NodeKind kind, LivingEntity entity) {
            return new PendingAction(kind, List.of(), entity);
        }

        private static PendingAction withBoth(
                PrecisionGraph.NodeKind kind,
                List<LivingEntity> entities,
                LivingEntity entity
        ) {
            return new PendingAction(kind, List.copyOf(entities), entity);
        }
    }

    private record ActiveContext(
            long expiresAt,
            Map<UUID, List<AutoCloseable>> subjectHandles,
            Map<UUID, Float> subjectCosts,
            float fixedCost,
            UUID intrusionSession,
            Set<UUID> targetIds
    ) {
        private float cost() {
            var total = fixedCost;
            for (var cost : subjectCosts.values()) total += cost;
            return total;
        }

        private void close(ServerPlayer player) {
            for (var handles : subjectHandles.values()) closeReverse(handles);
            if (player != null && intrusionSession != null) {
                MentalIntrusionManager.stopPrecision(player, intrusionSession);
            }
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

        private boolean references(UUID entityId) {
            return targetIds.contains(entityId);
        }
    }
}
