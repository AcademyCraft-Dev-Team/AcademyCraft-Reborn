package org.academy.internal.common.ability.mentalout.control;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.entitycontrol.*;
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

    private record ForceTargetBinding(Mob mob, UUID targetId) implements ControlBinding {

        @Override
            public void tick() {
                if (!mob.isAlive() || mob.isRemoved()) return;
                MentalControlRuntime.maintainTarget(mob);
            }

            @Override
            public void close() {
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
                return;
            }

            clearThreatTarget();
            if (mob.position().distanceToSqr(anchor.position()) <= arrivalRadiusSqr) {
                desired = null;
                navigator.stop();
                return;
            }
            desired = anchor;
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
                    || mob.isAlliedTo(candidate)
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
            return controller.isAlliedTo(entity) || FriendlyFireSetting.shouldPrevent(controller, entity);
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
        private static final int REPATH_INTERVAL_TICKS = 10;
        private static final int DYNAMIC_REPATH_INTERVAL_TICKS = 10;
        private static final int MAX_CONSECUTIVE_FAILURES = 3;
        private static final double MAX_DIRECT_APPROACH_DISTANCE = 3.25;
        private static final double DIRECT_COLLISION_SAMPLE_STEP = 0.25;

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
        private int consecutiveFailures;
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
                consecutiveFailures = 0;
                nextPathAttemptTime = gameTime;
                nextDynamicPathRefreshTime = gameTime + DYNAMIC_REPATH_INTERVAL_TICKS;
                lastDirectDistanceSqr = Double.NaN;
                lastDirectPosition = null;
            }

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
                if (navigation.getPath() == activePath) {
                    lastDirectDistanceSqr = Double.NaN;
                    lastDirectPosition = null;
                    consecutiveFailures = 0;
                    reinforceSpecialMovement(target, speed, aggressive);
                    return NavigationResult.MOVING;
                }
                navigation.stop();
                if (navigation.moveTo(activePath, speed)) {
                    lastDirectDistanceSqr = Double.NaN;
                    lastDirectPosition = null;
                    consecutiveFailures = 0;
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
                return recordFailure();
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
            consecutiveFailures = 0;
            reinforceSpecialMovement(target, speed, aggressive);
            return NavigationResult.MOVING;
        }

        private NavigationResult advanceDirect(ResolvedDestination target, double speed) {
            directApproach = true;
            mob.getNavigation().stop();
            applyDirectMovement(target, speed);

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
                consecutiveFailures = 0;
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
            consecutiveFailures++;
            return consecutiveFailures >= MAX_CONSECUTIVE_FAILURES
                    ? NavigationResult.FAILED
                    : NavigationResult.MOVING;
        }
    }
}
