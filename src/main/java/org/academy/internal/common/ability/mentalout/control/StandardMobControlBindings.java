package org.academy.internal.common.ability.mentalout.control;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.api.common.entitycontrol.*;
import org.academy.api.server.team.TeamRelations;
import org.academy.internal.common.world.damagesource.FriendlyFireSetting;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

final class StandardMobControlBindings {
    private StandardMobControlBindings() {
    }

    static ControlBinding create(ControlContext context, Mob mob, ControlDirective directive) {
        return switch (directive) {
            case ControlDirective.TakeoverAi ignored -> ControlBinding.noop();
            case ControlDirective.ForceTarget forceTarget -> new ForceTargetBinding(mob, forceTarget.targetUuid());
            case ControlDirective.FreezeAi ignored -> new FreezeBinding(mob);
            case ControlDirective.ImpressionAlliance ignored -> new RelationBinding(mob);
            case ControlDirective.MoveTo moveTo -> new PathBinding(
                    mob, moveTo.destination(), moveTo.arrivalRadius());
            case ControlDirective.LookAt lookAt -> new LookBinding(mob, lookAt.targetUuid());
            case ControlDirective.DirectControl ignored -> new MobDirectControlBinding(mob);
            case ControlDirective.Guard guard -> new GuardBinding(
                    mob,
                    context.controller(),
                    guard.destination(),
                    guard.detectionRadius(),
                    guard.arrivalRadius()
            );
        };
    }

    private static ResolvedDestination resolve(Mob mob, ControlDestination destination) {
        return switch (destination) {
            case ControlDestination.Entity entity -> {
                var target = MentalControlRuntime.findLivingEntity(mob.level().getServer(), entity.uuid());
                yield target == null || target.level() != mob.level() || !target.isAlive() || target.isRemoved()
                        ? null
                        : new ResolvedDestination(target.position(), target);
            }
            case ControlDestination.Position position ->
                    mob.level().dimension().identifier().equals(position.dimension())
                            ? new ResolvedDestination(position.value(), null)
                            : null;
        };
    }

    private enum NavigationResult {
        MOVING,
        ARRIVED,
        FAILED
    }

    /**
     * Generic forced-combat fallback for vanilla and third-party {@link Mob} subclasses.
     * It deliberately does not consult the subject's overridable target predicates: the public
     * control policy has already validated and authorized the target. Adapter-specific AI remains
     * free to attack first; its successful attacks postpone this fallback to avoid double hits.
     */
    private static final class ForceTargetBinding implements ControlBinding {
        private static final int FALLBACK_GRACE_TICKS = 2;
        private static final int ATTACK_INTERVAL_TICKS = 20;
        private static final double MAX_RANGED_ATTACK_DISTANCE = 16.0;

        private final Mob mob;
        private final UUID targetId;
        private final DestinationNavigator navigator;
        private LivingEntity target;
        private ResolvedDestination destination;
        private long nextFallbackAttackTime;
        private int lastObservedAttackTimestamp;
        private boolean rangedFallbackDisabled;

        private ForceTargetBinding(Mob mob, UUID targetId) {
            this.mob = mob;
            this.targetId = targetId;
            navigator = new DestinationNavigator(mob, 1.0);
            nextFallbackAttackTime = mob.level().getGameTime() + FALLBACK_GRACE_TICKS;
            lastObservedAttackTimestamp = mob.getLastHurtMobTimestamp();
        }

        @Override
        public void tick() {
            if (!mob.isAlive() || mob.isRemoved()) {
                navigator.stop();
                return;
            }
            var forcedTarget = MentalControlRuntime.getForcedTarget(mob);
            if (forcedTarget == null || !forcedTarget.getUUID().equals(targetId)) {
                target = null;
                destination = null;
                navigator.stop();
                return;
            }
            target = forcedTarget;
            destination = new ResolvedDestination(target.position(), target);
            MentalControlRuntime.maintainTarget(mob);
            reassertCombatIntent();
            observeAdapterAttack();

            if (!canExecuteCombat()) {
                navigator.stop();
                return;
            }
            if (isInAttackPosition()) {
                navigator.stop();
                attackIfReady();
            } else {
                navigator.advance(destination, 1.1, true);
            }
        }

        @Override
        public void beforeNavigationTick() {
            if (!canExecuteCombat() || destination == null || isInAttackPosition()) return;
            navigator.advance(destination, 1.1, true);
        }

        @Override
        public void beforeMoveControlTick() {
            if (!canExecuteCombat() || destination == null || isInAttackPosition()) return;
            navigator.reassert(destination, 1.1, true);
            reassertCombatIntent();
        }

        @Override
        public void beforeLookControlTick() {
            reassertCombatIntent();
        }

        private boolean canExecuteCombat() {
            if (target == null || !target.isAlive() || target.isRemoved()
                    || target.level() != mob.level() || MentalControlRuntime.isFrozen(mob)) return false;
            // A separately arbitrated movement/action command keeps ownership. Target policy is
            // still maintained, but its generic combat fallback must not steal that executor.
            return MentalControlRuntime.effectiveDirective(mob, ControlCapability.PATH_CONTROL).isEmpty()
                    && MentalControlRuntime.effectiveDirective(mob, ControlCapability.DIRECT_CONTROL).isEmpty()
                    && MentalControlRuntime.effectiveDirective(mob, ControlCapability.GUARD_CONTROL).isEmpty();
        }

        private boolean isInAttackPosition() {
            if (target == null || !hasClearAttackLine()) return false;
            if (mob instanceof RangedAttackMob && !rangedFallbackDisabled) {
                return mob.distanceToSqr(target)
                        <= MAX_RANGED_ATTACK_DISTANCE * MAX_RANGED_ATTACK_DISTANCE;
            }
            return mob.isWithinMeleeAttackRange(target);
        }

        private boolean hasClearAttackLine() {
            var hit = mob.level().clip(new ClipContext(
                    mob.getEyePosition(),
                    target.getEyePosition(),
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    mob
            ));
            return hit.getType() == HitResult.Type.MISS;
        }

        private void observeAdapterAttack() {
            var timestamp = mob.getLastHurtMobTimestamp();
            if (timestamp == lastObservedAttackTimestamp) return;
            lastObservedAttackTimestamp = timestamp;
            if (mob.getLastHurtMob() == target) {
                nextFallbackAttackTime = Math.max(
                        nextFallbackAttackTime,
                        mob.level().getGameTime() + ATTACK_INTERVAL_TICKS
                );
            }
        }

        private void attackIfReady() {
            var gameTime = mob.level().getGameTime();
            if (gameTime < nextFallbackAttackTime
                    || !(mob.level() instanceof net.minecraft.server.level.ServerLevel level)) return;

            var attacked = false;
            if (mob instanceof RangedAttackMob ranged && !rangedFallbackDisabled) {
                try {
                    ranged.performRangedAttack(target, 1.0F);
                    attacked = true;
                } catch (RuntimeException exception) {
                    rangedFallbackDisabled = true;
                    AcademyCraft.LOGGER.debug(
                            "Forced-combat ranged fallback is unavailable for {}; using melee",
                            mob.getType(),
                            exception
                    );
                }
            }
            if (!attacked && mob.isWithinMeleeAttackRange(target)) {
                mob.swing(InteractionHand.MAIN_HAND, true);
                mob.doHurtTarget(level, target);
                attacked = true;
            }
            lastObservedAttackTimestamp = mob.getLastHurtMobTimestamp();
            nextFallbackAttackTime = gameTime + (attacked
                    ? ATTACK_INTERVAL_TICKS
                    : FALLBACK_GRACE_TICKS);
        }

        private void reassertCombatIntent() {
            if (target == null || !target.isAlive() || target.isRemoved()) return;
            mob.setAggressive(true);
            mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        }

        @Override
        public void close() {
            navigator.stop();
            if (!(mob instanceof MentalControlMobAccess access)) return;
            var current = access.academy$getRawMentalControlTarget();
            if (current != null && current.getUUID().equals(targetId)
                    && MentalControlRuntime.getForcedTarget(mob) == null) {
                mob.setTarget(null);
            }
            var memory = mob.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET);
            if (memory != null && memory.isPresent() && memory.get().getUUID().equals(targetId)
                    && MentalControlRuntime.getForcedTarget(mob) == null) {
                mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            }
            if (MentalControlRuntime.getForcedTarget(mob) == null) mob.setAggressive(false);
        }
    }

    private record FreezeBinding(Mob mob) implements ControlBinding {

        @Override
        public void tick() {
            if (!mob.isAlive() || mob.isRemoved()) return;
            var verticalVelocity = Math.min(0.0, mob.getDeltaMovement().y);
            mob.stopInPlace();
            mob.getNavigation().stop();
            mob.setJumping(false);
            mob.setDeltaMovement(0.0, verticalVelocity, 0.0);
        }

        @Override
        public void close() {
        }
    }

    private record RelationBinding(Mob mob) implements ControlBinding {

        @Override
        public void tick() {
            if (mob.isAlive() && !mob.isRemoved()) {
                MentalControlRuntime.enforceTargetWhitelist(mob);
            }
        }

        @Override
        public void close() {
        }
    }

    private static final class PathBinding implements ControlBinding {
        private final Mob mob;
        private final ControlDestination destination;
        private final double arrivalRadiusSqr;
        private final DestinationNavigator navigator;
        private ResolvedDestination target;
        private boolean complete;
        private ControlFailureReason failureReason;
        private int invalidDestinationTicks;

        private PathBinding(Mob mob, ControlDestination destination, double arrivalRadius) {
            this.mob = mob;
            this.destination = destination;
            arrivalRadiusSqr = arrivalRadius * arrivalRadius;
            navigator = new DestinationNavigator(mob, arrivalRadius);
        }

        @Override
        public void tick() {
            if (complete || !mob.isAlive() || mob.isRemoved()) return;
            var target = resolve(mob, destination);
            if (target == null) {
                fail(ControlFailureReason.TARGET_UNAVAILABLE);
                return;
            }
            this.target = target;
            if (mob.position().distanceToSqr(target.position()) <= arrivalRadiusSqr) {
                complete = true;
                navigator.stop();
                return;
            }
            if (target.entity() == null && !navigator.canOccupyTarget(target.position())) {
                if (++invalidDestinationTicks >= 10) {
                    fail(ControlFailureReason.UNREACHABLE_DESTINATION);
                }
                return;
            }
            invalidDestinationTicks = 0;
            switch (navigator.advance(target, 1.0, false)) {
                case ARRIVED -> complete = true;
                case FAILED -> fail(ControlFailureReason.UNREACHABLE_DESTINATION);
                case MOVING -> {
                }
            }
        }

        @Override
        public void beforeNavigationTick() {
            if (complete || target == null) return;
            switch (navigator.advance(target, 1.0, false)) {
                case ARRIVED -> complete = true;
                case FAILED -> fail(ControlFailureReason.UNREACHABLE_DESTINATION);
                case MOVING -> {
                }
            }
        }

        @Override
        public void beforeMoveControlTick() {
            // Flying mobs commonly replace their velocity or wanted position from MoveControl.
            // Reassert after navigation and immediately before that tick so the controlled
            // destination remains the final movement owner for this server tick.
            if (!complete && target != null) navigator.reassert(target, 1.0, false);
        }

        @Override
        public boolean isComplete() {
            return complete;
        }

        @Override
        public Optional<ControlFailureReason> failureReason() {
            return Optional.ofNullable(failureReason);
        }

        private void fail(ControlFailureReason reason) {
            failureReason = reason;
            complete = true;
            navigator.stop();
        }

        @Override
        public void close() {
            if (MentalControlRuntime.effectiveDirective(mob, ControlCapability.PATH_CONTROL).isEmpty()) {
                navigator.stop();
            }
        }
    }

    private static final class GuardBinding implements ControlBinding {
        private static final int SCAN_INTERVAL_TICKS = 5;
        private static final int RECENT_HOSTILITY_TICKS = 100;

        private final Mob mob;
        private final ServerPlayer controller;
        private final ControlDestination destination;
        private final double detectionRadius;
        private final double detectionRadiusSqr;
        private final double arrivalRadiusSqr;
        private final DestinationNavigator navigator;
        private LivingEntity threat;
        private ResolvedDestination desired;
        private boolean complete;
        private ControlFailureReason failureReason;
        private int scanTicker;

        private GuardBinding(
                Mob mob,
                ServerPlayer controller,
                ControlDestination destination,
                double detectionRadius,
                double arrivalRadius
        ) {
            this.mob = mob;
            this.controller = controller;
            this.destination = destination;
            this.detectionRadius = detectionRadius;
            detectionRadiusSqr = detectionRadius * detectionRadius;
            arrivalRadiusSqr = arrivalRadius * arrivalRadius;
            navigator = new DestinationNavigator(mob, arrivalRadius);
        }

        @Override
        public void tick() {
            if (complete || !mob.isAlive() || mob.isRemoved()) return;
            var anchor = resolve(mob, destination);
            if (anchor == null) {
                failureReason = ControlFailureReason.TARGET_UNAVAILABLE;
                complete = true;
                clearThreat();
                return;
            }

            scanTicker++;
            if (scanTicker >= SCAN_INTERVAL_TICKS || !validThreat(threat, anchor)) {
                scanTicker = 0;
                threat = findThreat(anchor);
            }
            if (threat != null) {
                MentalControlRuntime.authorizeRetaliation(mob, threat);
                MentalControlRuntime.updateGuardTarget(mob, threat);
                if (mob.getTarget() != threat) mob.setTarget(threat);
                mob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, threat);
                desired = new ResolvedDestination(threat.position(), threat);
                advanceDesired();
                return;
            }

            clearThreatTarget();
            if (mob.position().distanceToSqr(anchor.position()) <= arrivalRadiusSqr) {
                desired = null;
                navigator.stop();
                return;
            }
            desired = anchor;
            advanceDesired();
        }

        private void advanceDesired() {
            if (complete || desired == null) return;
            var result = navigator.advance(desired, threat == null ? 1.0 : 1.1, threat != null);
            if (result == NavigationResult.FAILED) {
                failureReason = ControlFailureReason.UNREACHABLE_DESTINATION;
                complete = true;
                clearThreat();
            }
        }

        @Override
        public void beforeNavigationTick() {
            if (complete || desired == null) return;
            var result = navigator.advance(desired, threat == null ? 1.0 : 1.1, threat != null);
            if (result == NavigationResult.FAILED) {
                failureReason = ControlFailureReason.UNREACHABLE_DESTINATION;
                complete = true;
                clearThreat();
            }
        }

        @Override
        public void beforeMoveControlTick() {
            if (!complete && desired != null) {
                navigator.reassert(desired, threat == null ? 1.0 : 1.1, threat != null);
            }
        }

        private LivingEntity findThreat(ResolvedDestination anchor) {
            var point = anchor.position();
            return mob.level().getEntitiesOfClass(
                            LivingEntity.class,
                            new AABB(point, point).inflate(detectionRadius),
                            candidate -> validThreat(candidate, anchor)
                    ).stream()
                    .min(Comparator.comparingDouble((LivingEntity candidate) ->
                                    candidate.position().distanceToSqr(point))
                            .thenComparing(LivingEntity::getUUID))
                    .orElse(null);
        }

        private boolean validThreat(LivingEntity candidate, ResolvedDestination anchor) {
            if (candidate == null || candidate == mob || candidate == controller
                    || !candidate.isAlive() || candidate.isRemoved()
                    || candidate.level() != mob.level()
                    || candidate.position().distanceToSqr(anchor.position()) > detectionRadiusSqr
                    || isControllerAlly(candidate)
                    || TeamRelations.areAllied(mob, candidate)
                    || MentalPerceptionRuntime.decision(mob, candidate)
                    == PerceptionDecision.HIDDEN) {
                return false;
            }
            var currentTarget = candidate instanceof Mob candidateMob
                    ? MentalControlRuntime.getForcedTarget(candidateMob) != null
                    ? MentalControlRuntime.getForcedTarget(candidateMob)
                    : candidateMob.getTarget()
                    : null;
            var hostile = isProtected(currentTarget, anchor.entity());
            var lastVictim = candidate.getLastHurtMob();
            hostile |= candidate.tickCount - candidate.getLastHurtMobTimestamp() <= RECENT_HOSTILITY_TICKS
                    && isProtected(lastVictim, anchor.entity());
            return hostile;
        }

        private boolean isProtected(LivingEntity entity, LivingEntity anchorEntity) {
            return entity != null && (entity == anchorEntity || entity == controller || isControllerAlly(entity));
        }

        private boolean isControllerAlly(LivingEntity entity) {
            return TeamRelations.areAllied(controller, entity) || FriendlyFireSetting.shouldPrevent(controller, entity);
        }

        private void clearThreatTarget() {
            MentalControlRuntime.updateGuardTarget(mob, null);
            if (mob.getTarget() != null) {
                mob.setTarget(null);
                mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            }
            threat = null;
        }

        private void clearThreat() {
            clearThreatTarget();
            desired = null;
            navigator.stop();
        }

        @Override
        public boolean isComplete() {
            return complete;
        }

        @Override
        public Optional<ControlFailureReason> failureReason() {
            return Optional.ofNullable(failureReason);
        }

        @Override
        public void close() {
            clearThreat();
            if (MentalControlRuntime.effectiveDirective(mob, ControlCapability.GUARD_CONTROL).isEmpty()) {
                navigator.stop();
            }
        }
    }

    private record LookBinding(Mob mob, UUID targetId) implements ControlBinding {

        @Override
        public void tick() {
            applyLook();
        }

        @Override
        public void beforeLookControlTick() {
            applyLook();
        }

        private void applyLook() {
            if (!mob.isAlive() || mob.isRemoved()) return;
            var target = MentalControlRuntime.findLivingEntity(mob.level().getServer(), targetId);
            if (target == null || target.level() != mob.level() || !target.isAlive() || target.isRemoved()) return;
            mob.getLookControl().setLookAt(target, 30.0f, 30.0f);
        }

        @Override
        public void close() {
        }
    }

    private record ResolvedDestination(Vec3 position, LivingEntity entity) {
        private BlockPos blockPosition() {
            return BlockPos.containing(position);
        }
    }

    private static final class DestinationNavigator {
        private static final int REPATH_INTERVAL_TICKS = 2;
        private static final int DYNAMIC_REPATH_INTERVAL_TICKS = 4;
        private static final int NO_PROGRESS_TIMEOUT_TICKS = 160;
        private static final int STALLED_PATH_REPLAN_TICKS = 20;
        private static final int INVALID_DESTINATION_GRACE_TICKS = 10;
        private static final double MAX_DIRECT_APPROACH_DISTANCE = 8.0;
        private static final double DIRECT_COLLISION_SAMPLE_STEP = 0.25;
        private static final double DIRECT_STEP = 0.12;

        private final Mob mob;
        private final int reachRange;
        private final double arrivalRadiusSqr;
        private BlockPos requestedTarget;
        private UUID requestedEntity;
        private Path activePath;
        private long nextPathAttemptTime;
        private long nextDynamicPathRefreshTime;
        private long nextDirectProgressCheckTime;
        private long lastAdvanceTime = Long.MIN_VALUE;
        private long lastProgressTime;
        private double bestDistanceSqr = Double.MAX_VALUE;
        private double lastDirectDistanceSqr = Double.NaN;
        private Vec3 lastDirectPosition;
        private boolean directApproach;

        private DestinationNavigator(Mob mob, double arrivalRadius) {
            this.mob = mob;
            // PathFinder uses Manhattan block distance, which may stop more than one Euclidean
            // block away when given a reach range of one. Request the next tighter path and let
            // the precise radius check above decide when the destination has been reached.
            reachRange = Math.max(0, Mth.ceil(arrivalRadius) - 1);
            arrivalRadiusSqr = arrivalRadius * arrivalRadius;
        }

        private NavigationResult advance(ResolvedDestination target, double speed, boolean aggressive) {
            if (mob.position().distanceToSqr(target.position()) <= arrivalRadiusSqr) {
                stop();
                return NavigationResult.ARRIVED;
            }

            var gameTime = mob.level().getGameTime();
            if (lastAdvanceTime == gameTime) {
                reassert(target, speed, aggressive);
                return NavigationResult.MOVING;
            }
            lastAdvanceTime = gameTime;

            var targetBlock = target.blockPosition();
            var targetEntity = target.entity() == null ? null : target.entity().getUUID();
            var entityChanged = !Objects.equals(targetEntity, requestedEntity);
            var positionChanged = !targetBlock.equals(requestedTarget);
            var changed = requestedTarget == null
                    || entityChanged
                    || targetEntity == null && positionChanged
                    || targetEntity != null && positionChanged
                    && gameTime >= nextDynamicPathRefreshTime;
            if (changed) {
                requestedTarget = targetBlock;
                requestedEntity = targetEntity;
                activePath = null;
                directApproach = false;
                lastProgressTime = gameTime;
                bestDistanceSqr = mob.position().distanceToSqr(target.position());
                nextPathAttemptTime = gameTime;
                nextDynamicPathRefreshTime = gameTime + DYNAMIC_REPATH_INTERVAL_TICKS;
                lastDirectDistanceSqr = Double.NaN;
                lastDirectPosition = null;
            }

            recordProgress(target, gameTime);

            if (usesDirectMovement()) return advanceDirect(target, speed);

            var navigation = mob.getNavigation();
            if (activePath != null && activePath.isDone()) {
                // A vanilla path may end at its last reachable node while still outside our
                // precise one-block radius. Rebuild immediately instead of leaving a ten-tick
                // window in which a vanilla goal can take ownership of navigation again.
                activePath = null;
                nextPathAttemptTime = gameTime;
                if (hasClearDirectApproach(target.position())) {
                    return advanceDirect(target, speed);
                }
            }
            if (activePath != null && !activePath.isDone()) {
                directApproach = false;
                if (gameTime - lastProgressTime >= STALLED_PATH_REPLAN_TICKS) {
                    navigation.stop();
                    activePath = null;
                    nextPathAttemptTime = gameTime;
                    if (isCubeMob() || hasClearDirectApproach(target.position())) {
                        return advanceDirect(target, speed);
                    }
                }
            }
            if (activePath != null && !activePath.isDone()) {
                if (navigation.getPath() == activePath) {
                    lastDirectDistanceSqr = Double.NaN;
                    lastDirectPosition = null;
                    reinforceSpecialMovement(target, speed, aggressive);
                    return NavigationResult.MOVING;
                }
                navigation.stop();
                if (navigation.moveTo(activePath, speed)) {
                    lastDirectDistanceSqr = Double.NaN;
                    lastDirectPosition = null;
                    reinforceSpecialMovement(target, speed, aggressive);
                    return NavigationResult.MOVING;
                }
                activePath = null;
            }
            if (gameTime < nextPathAttemptTime) {
                if (isCubeMob()) return advanceDirect(target, speed);
                if (hasClearDirectApproach(target.position())) {
                    return advanceDirect(target, speed);
                }
                navigation.stop();
                stopSpecialMovement();
                return NavigationResult.MOVING;
            }

            if (target.entity() == null && !canOccupy(target.position())) {
                activePath = null;
                nextPathAttemptTime = gameTime + REPATH_INTERVAL_TICKS;
                return gameTime - lastProgressTime >= INVALID_DESTINATION_GRACE_TICKS
                        ? NavigationResult.FAILED
                        : NavigationResult.MOVING;
            }

            activePath = target.entity() != null
                    ? navigation.createPath(target.entity(), reachRange)
                    : navigation.createPath(targetBlock, reachRange);
            nextPathAttemptTime = gameTime + REPATH_INTERVAL_TICKS;
            if (activePath == null) {
                activePath = null;
                if (isCubeMob()) return advanceDirect(target, speed);
                if (hasClearDirectApproach(target.position())) {
                    return advanceDirect(target, speed);
                }
                return recordFailure();
            }
            navigation.stop();
            if (!navigation.moveTo(activePath, speed)) {
                activePath = null;
                if (isCubeMob()) return advanceDirect(target, speed);
                if (hasClearDirectApproach(target.position())) {
                    return advanceDirect(target, speed);
                }
                return recordFailure();
            }
            directApproach = false;
            reinforceSpecialMovement(target, speed, aggressive);
            return NavigationResult.MOVING;
        }

        private NavigationResult advanceDirect(ResolvedDestination target, double speed) {
            directApproach = true;
            mob.getNavigation().stop();
            applyDirectMovement(target, speed);
            advanceDirectPosition(target.position(), speed);
            recordProgress(target, mob.level().getGameTime());

            var distanceSqr = mob.position().distanceToSqr(target.position());
            if (!Double.isFinite(lastDirectDistanceSqr)) {
                lastDirectDistanceSqr = distanceSqr;
                lastDirectPosition = mob.position();
                nextDirectProgressCheckTime = mob.level().getGameTime() + REPATH_INTERVAL_TICKS;
                return NavigationResult.MOVING;
            }
            if (mob.level().getGameTime() < nextDirectProgressCheckTime) {
                return NavigationResult.MOVING;
            }

            nextDirectProgressCheckTime = mob.level().getGameTime() + REPATH_INTERVAL_TICKS;
            var position = mob.position();
            if (distanceSqr < lastDirectDistanceSqr - 0.01
                    || lastDirectPosition == null
                    || position.distanceToSqr(lastDirectPosition) > 0.04) {
                recordProgress(target, mob.level().getGameTime());
            } else if (recordFailure() == NavigationResult.FAILED) {
                stop();
                return NavigationResult.FAILED;
            }
            lastDirectDistanceSqr = distanceSqr;
            lastDirectPosition = position;
            return NavigationResult.MOVING;
        }

        private void applyDirectMovement(ResolvedDestination target, double speed) {
            if (isCubeMob()) {
                reinforceSpecialMovement(target, speed, false);
            } else if (mob instanceof DirectMobMovementAccess directMovement) {
                directMovement.academy$moveDirectly(target.position(), speed);
            } else {
                mob.getMoveControl().setWantedPosition(
                        target.position().x,
                        target.position().y,
                        target.position().z,
                        speed
                );
            }
        }

        private void advanceDirectPosition(Vec3 destination, double speed) {
            var delta = destination.subtract(mob.position());
            var distance = delta.length();
            if (distance <= 1.0E-6) return;
            var step = delta.scale(Math.min(distance, DIRECT_STEP * speed) / distance);
            if (mob.level().noCollision(mob, mob.getBoundingBox().move(step))) {
                mob.move(MoverType.SELF, step);
            }
        }

        private void reassert(ResolvedDestination target, double speed, boolean aggressive) {
            if (mob.position().distanceToSqr(target.position()) <= arrivalRadiusSqr) return;
            if (directApproach || usesDirectMovement()) {
                applyDirectMovement(target, speed);
                return;
            }

            var navigation = mob.getNavigation();
            if (activePath != null && !activePath.isDone() && navigation.getPath() != activePath) {
                navigation.stop();
                navigation.moveTo(activePath, speed);
            }
            if (activePath != null && !activePath.isDone()) {
                var waypoint = activePath.getNextEntityPos(mob);
                mob.getMoveControl().setWantedPosition(waypoint.x, waypoint.y, waypoint.z, speed);
            } else {
                // The exclusive movement lease prevents goals and custom AI from issuing a new
                // route. Keep the entity still while waiting for the next controlled path attempt.
                navigation.stop();
                mob.getMoveControl().setWantedPosition(
                        mob.getX(),
                        mob.getY(),
                        mob.getZ(),
                        0.0
                );
            }
            reinforceSpecialMovement(target, speed, aggressive);
        }

        private boolean usesDirectMovement() {
            return mob instanceof Ghast || mob instanceof Vex || mob instanceof DirectMobMovementAccess;
        }

        private boolean isCubeMob() {
            return mob.getMoveControl() instanceof CubeMobMoveControlAccess;
        }

        private boolean canOccupy(Vec3 position) {
            var offset = position.subtract(mob.position());
            return mob.level().noCollision(mob, mob.getBoundingBox().move(offset));
        }

        private boolean canOccupyTarget(Vec3 position) {
            return canOccupy(position);
        }

        private boolean hasClearDirectApproach(Vec3 position) {
            var delta = position.subtract(mob.position());
            var distance = delta.length();
            if (distance > MAX_DIRECT_APPROACH_DISTANCE || !canOccupy(position)) return false;
            var samples = Math.max(1, Mth.ceil(distance / DIRECT_COLLISION_SAMPLE_STEP));
            var bounds = mob.getBoundingBox();
            for (var sample = 1; sample <= samples; sample++) {
                var offset = delta.scale((double) sample / samples);
                if (!mob.level().noCollision(mob, bounds.move(offset))) return false;
            }
            return true;
        }

        private void reinforceSpecialMovement(
                ResolvedDestination target,
                double speed,
                boolean aggressive
        ) {
            if (!(mob.getMoveControl() instanceof CubeMobMoveControlAccess cubeMove)) return;
            var waypoint = activePath != null && !activePath.isDone()
                    ? activePath.getNextEntityPos(mob)
                    : target.position();
            var dx = waypoint.x - mob.getX();
            var dz = waypoint.z - mob.getZ();
            if (dx * dx + dz * dz <= arrivalRadiusSqr) {
                cubeMove.academy$setMentalControlMovement(0.0);
                var movement = mob.getDeltaMovement();
                mob.setDeltaMovement(movement.x * 0.25, movement.y, movement.z * 0.25);
                return;
            }
            if (dx * dx + dz * dz > 1.0E-6) {
                var yRot = (float) (Mth.atan2(dz, dx) * 180.0 / Mth.PI) - 90.0F;
                // Cube mobs only jump when their controller's aggressive cadence expires. Use
                // that shorter cadence for mental navigation as well; it changes no target or
                // damage decision, but prevents a valid route from spending most of its lease idle.
                cubeMove.academy$setMentalControlDirection(yRot, true);
            }
            cubeMove.academy$setMentalControlMovement(speed);
            if (dx * dx + dz * dz > 1.0E-6) {
                var horizontalLength = Mth.sqrt((float) (dx * dx + dz * dz));
                var movement = mob.getDeltaMovement();
                var desiredSpeed = 0.16 * speed;
                mob.setDeltaMovement(
                        Mth.lerp(0.35, movement.x, dx / horizontalLength * desiredSpeed),
                        movement.y,
                        Mth.lerp(0.35, movement.z, dz / horizontalLength * desiredSpeed)
                );
            }
        }

        private void stopSpecialMovement() {
            if (mob.getMoveControl() instanceof CubeMobMoveControlAccess cubeMove) {
                cubeMove.academy$setMentalControlMovement(0.0);
            }
            if (mob instanceof DirectMobMovementAccess directMovement) {
                directMovement.academy$stopDirectMovement();
            }
            if (usesDirectMovement()) {
                mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), 0.0);
                mob.stopInPlace();
            }
        }

        private void stop() {
            mob.getNavigation().stop();
            activePath = null;
            directApproach = false;
            stopSpecialMovement();
        }

        private NavigationResult recordFailure() {
            return mob.level().getGameTime() - lastProgressTime >= NO_PROGRESS_TIMEOUT_TICKS
                    ? NavigationResult.FAILED
                    : NavigationResult.MOVING;
        }

        private void recordProgress(ResolvedDestination target, long gameTime) {
            var distanceSqr = mob.position().distanceToSqr(target.position());
            if (distanceSqr < bestDistanceSqr - 0.01) {
                bestDistanceSqr = distanceSqr;
                lastProgressTime = gameTime;
            }
        }
    }
}
