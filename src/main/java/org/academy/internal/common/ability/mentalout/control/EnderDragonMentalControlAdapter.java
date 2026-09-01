package org.academy.internal.common.ability.mentalout.control;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.entitycontrol.*;
import org.academy.api.server.team.TeamRelations;
import org.academy.internal.common.ability.mentalout.PlayerControlSessionManager;
import org.academy.internal.common.world.damagesource.FriendlyFireSetting;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

public final class EnderDragonMentalControlAdapter implements MentalControlAdapter {
    private static void strafeTarget(EnderDragon dragon, LivingEntity target) {
        var manager = dragon.getPhaseManager();
        if (manager.getCurrentPhase().getPhase() != EnderDragonPhase.STRAFE_PLAYER) {
            manager.setPhase(EnderDragonPhase.STRAFE_PLAYER);
        }
        manager.getPhase(EnderDragonPhase.STRAFE_PLAYER).setTarget(target);
    }

    private static void setFlightTarget(EnderDragon dragon, Vec3 target) {
        var manager = dragon.getPhaseManager();
        if (manager.getCurrentPhase().getPhase() != EnderDragonPhase.HOVERING) {
            manager.setPhase(EnderDragonPhase.HOVERING);
        }
        ((DragonHoverPhaseAccess) manager.getPhase(EnderDragonPhase.HOVERING))
                .academy$setMentalControlFlightTarget(target);
    }

    private static void lookAt(EnderDragon dragon, Vec3 target) {
        var delta = target.subtract(dragon.getEyePosition());
        var horizontal = Mth.sqrt((float) (delta.x * delta.x + delta.z * delta.z));
        dragon.setYRot((float) (Mth.atan2(delta.z, delta.x) * 180.0 / Mth.PI) - 90.0F);
        dragon.yHeadRot = dragon.getYRot();
        dragon.setXRot((float) -(Mth.atan2(delta.y, horizontal) * 180.0 / Mth.PI));
    }

    private static void steerFlight(EnderDragon dragon, Vec3 target) {
        var delta = target.subtract(dragon.position());
        if (delta.x * delta.x + delta.z * delta.z <= 1.0E-6) return;
        dragon.setYRot(Mth.wrapDegrees(
                180.0F - (float) (Mth.atan2(delta.x, delta.z)) * Mth.RAD_TO_DEG));
        dragon.yBodyRot = dragon.getYRot();
    }

    private static void advanceFlight(EnderDragon dragon, Vec3 target, double maximumStep) {
        var delta = target.subtract(dragon.position());
        var distance = delta.length();
        if (distance <= 1.0E-6) return;
        dragon.move(MoverType.SELF, delta.scale(Math.min(distance, maximumStep) / distance));
    }

    private static ResolvedDestination resolve(EnderDragon dragon, ControlDestination destination) {
        return switch (destination) {
            case ControlDestination.Entity entity -> {
                var target = MentalControlRuntime.findLivingEntity(dragon.level().getServer(), entity.uuid());
                yield target == null || target.level() != dragon.level()
                        || !target.isAlive() || target.isRemoved()
                        ? null
                        : new ResolvedDestination(target.position(), target);
            }
            case ControlDestination.Position position ->
                    dragon.level().dimension().identifier().equals(position.dimension())
                            ? new ResolvedDestination(position.value(), null)
                            : null;
        };
    }

    @Override
    public boolean matches(LivingEntity subject) {
        return subject instanceof EnderDragon;
    }

    @Override
    public ControlSupport support(LivingEntity subject, ControlCapability capability) {
        return matches(subject) ? ControlSupport.FULL : ControlSupport.UNSUPPORTED;
    }

    @Override
    public ControlBinding activate(ControlContext context, ControlDirective directive) {
        if (!(context.subject() instanceof EnderDragon dragon)) {
            throw new IllegalArgumentException("Ender Dragon adapter requires an Ender Dragon subject");
        }
        return switch (directive) {
            case ControlDirective.TakeoverAi ignored -> ControlBinding.noop();
            case ControlDirective.ForceTarget forceTarget -> new ForceTargetBinding(dragon, forceTarget.targetUuid());
            case ControlDirective.FreezeAi ignored -> new FreezeBinding(dragon);
            case ControlDirective.ImpressionAlliance ignored -> ControlBinding.noop();
            case ControlDirective.MoveTo moveTo -> new MoveBinding(
                    dragon, moveTo.destination(), moveTo.arrivalRadius());
            case ControlDirective.LookAt lookAt -> new LookBinding(dragon, lookAt.targetUuid());
            case ControlDirective.DirectControl ignored -> new DirectBinding(dragon);
            case ControlDirective.Guard guard -> new GuardBinding(
                    dragon,
                    context.controller(),
                    guard.destination(),
                    guard.detectionRadius(),
                    guard.arrivalRadius()
            );
        };
    }

    private static final class DirectBinding implements ControlBinding {
        private final EnderDragon dragon;
        private PlayerControlFrame frame = PlayerControlFrame.NEUTRAL;
        private long lastActionSequence = Long.MIN_VALUE;

        private DirectBinding(EnderDragon dragon) {
            this.dragon = dragon;
        }

        @Override
        public void tick() {
            var input = PlayerControlSessionManager.mobDirectInput(dragon).orElse(null);
            if (input == null) {
                frame = PlayerControlFrame.NEUTRAL;
                setFlightTarget(dragon, dragon.position());
                return;
            }
            frame = input.frame();
            var forward = Vec3.directionFromRotation(frame.pitch(), frame.yaw());
            var right = new Vec3(-forward.z, 0.0, forward.x);
            var movement = forward.scale(frame.forward()).add(right.scale(frame.strafe()));
            movement = movement.add(0.0,
                    (frame.jump() ? 1.0 : 0.0) - (frame.sneak() ? 1.0 : 0.0), 0.0);
            if (movement.lengthSqr() > 1.0e-6) {
                movement = movement.normalize();
                var target = dragon.position().add(movement.scale(24.0));
                setFlightTarget(dragon, target);
                steerFlight(dragon, target);
                advanceFlight(dragon, target, frame.sprint() ? 0.24 : 0.16);
            } else {
                dragon.setDeltaMovement(Vec3.ZERO);
                setFlightTarget(dragon, dragon.position());
                lookAt(dragon, dragon.getEyePosition().add(forward.scale(8.0)));
            }
            if (input.sequence() != lastActionSequence) {
                lastActionSequence = input.sequence();
                if (frame.attack()) attack();
            }
        }

        private void attack() {
            if (!(dragon.level() instanceof ServerLevel level)) return;
            var eye = dragon.getEyePosition();
            var end = eye.add(Vec3.directionFromRotation(frame.pitch(), frame.yaw()).scale(12.0));
            var block = level.clip(new ClipContext(
                    eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, dragon));
            var rayEnd = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
            var hit = ProjectileUtil.getEntityHitResult(
                    level,
                    dragon,
                    eye,
                    rayEnd,
                    new AABB(eye, rayEnd).inflate(2.0),
                    entity -> entity instanceof LivingEntity living && living != dragon
                            && living.isAlive() && living.isPickable() && !living.isSpectator(),
                    0.3f
            );
            if (hit != null && hit.getEntity() instanceof LivingEntity target
                    && MentalControlRuntime.attackDecision(dragon, target) != AttackDecision.DENY) {
                dragon.doHurtTarget(level, target);
            }
        }

        @Override
        public void close() {
            dragon.setDeltaMovement(Vec3.ZERO);
            if (dragon.getPhaseManager().getCurrentPhase().getPhase() == EnderDragonPhase.HOVERING) {
                dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
            }
        }
    }

    private record ForceTargetBinding(EnderDragon dragon, UUID targetId) implements ControlBinding {

        @Override
        public void tick() {
            if (MentalControlRuntime.isFrozen(dragon)
                    || !(dragon.level() instanceof ServerLevel level)
                    || !(level.getEntity(targetId) instanceof LivingEntity target)
                    || !target.isAlive() || target.isRemoved()) return;
            strafeTarget(dragon, target);
        }

        @Override
        public void close() {
            if (MentalControlRuntime.getForcedTarget(dragon) == null
                    && dragon.getPhaseManager().getCurrentPhase().getPhase()
                    == EnderDragonPhase.STRAFE_PLAYER) {
                dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
            }
        }
    }

    private record FreezeBinding(EnderDragon dragon) implements ControlBinding {

        @Override
        public void tick() {
            if (dragon.isDeadOrDying() || dragon.getHealth() <= 0.0F
                    || dragon.getPhaseManager().getCurrentPhase().getPhase() == EnderDragonPhase.DYING) return;
            dragon.setDeltaMovement(Vec3.ZERO);
            setFlightTarget(dragon, dragon.position());
        }

        @Override
        public void close() {
            if (!MentalControlRuntime.isFrozen(dragon)
                    && dragon.getPhaseManager().getCurrentPhase().getPhase() == EnderDragonPhase.HOVERING) {
                dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
            }
        }
    }

    private static final class MoveBinding implements ControlBinding {
        private final EnderDragon dragon;
        private final ControlDestination destination;
        private final double arrivalRadiusSqr;
        private boolean complete;
        private ControlFailureReason failureReason;

        private MoveBinding(EnderDragon dragon, ControlDestination destination, double arrivalRadius) {
            this.dragon = dragon;
            this.destination = destination;
            arrivalRadiusSqr = arrivalRadius * arrivalRadius;
        }

        @Override
        public void tick() {
            if (complete || !dragon.isAlive() || dragon.isRemoved()) return;
            var resolved = resolve(dragon, destination);
            if (resolved == null) {
                failureReason = ControlFailureReason.TARGET_UNAVAILABLE;
                complete = true;
                return;
            }
            if (dragon.position().distanceToSqr(resolved.position()) <= arrivalRadiusSqr) {
                complete = true;
                dragon.setDeltaMovement(Vec3.ZERO);
                setFlightTarget(dragon, dragon.position());
                return;
            }
            steerFlight(dragon, resolved.position());
            setFlightTarget(dragon, resolved.position());
            advanceFlight(dragon, resolved.position(), 0.10);
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
            if (MentalControlRuntime.effectiveDirective(dragon, ControlCapability.PATH_CONTROL).isEmpty()
                    && dragon.getPhaseManager().getCurrentPhase().getPhase() == EnderDragonPhase.HOVERING) {
                dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
            }
        }
    }

    private static final class LookBinding implements ControlBinding {
        private final EnderDragon dragon;
        private final UUID targetId;
        private boolean complete;
        private ControlFailureReason failureReason;

        private LookBinding(EnderDragon dragon, UUID targetId) {
            this.dragon = dragon;
            this.targetId = targetId;
        }

        @Override
        public void tick() {
            applyLook();
        }

        @Override
        public void beforeLookControlTick() {
            applyLook();
        }

        private void applyLook() {
            if (complete) return;
            var target = MentalControlRuntime.findLivingEntity(dragon.level().getServer(), targetId);
            if (target == null || target.level() != dragon.level() || !target.isAlive() || target.isRemoved()) {
                complete = true;
                failureReason = ControlFailureReason.TARGET_UNAVAILABLE;
                return;
            }
            lookAt(dragon, target.getEyePosition());
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
        }
    }

    private static final class GuardBinding implements ControlBinding {
        private static final int SCAN_INTERVAL_TICKS = 5;
        private static final int RECENT_HOSTILITY_TICKS = 100;

        private final EnderDragon dragon;
        private final ServerPlayer controller;
        private final ControlDestination destination;
        private final double detectionRadius;
        private final double detectionRadiusSqr;
        private final double arrivalRadiusSqr;
        private LivingEntity threat;
        private boolean complete;
        private ControlFailureReason failureReason;
        private int scanTicker;

        private GuardBinding(
                EnderDragon dragon,
                ServerPlayer controller,
                ControlDestination destination,
                double detectionRadius,
                double arrivalRadius
        ) {
            this.dragon = dragon;
            this.controller = controller;
            this.destination = destination;
            this.detectionRadius = detectionRadius;
            detectionRadiusSqr = detectionRadius * detectionRadius;
            arrivalRadiusSqr = arrivalRadius * arrivalRadius;
        }

        @Override
        public void tick() {
            if (complete || !dragon.isAlive() || dragon.isRemoved()) return;
            var anchor = resolve(dragon, destination);
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
                MentalControlRuntime.updateGuardTarget(dragon, threat);
                strafeTarget(dragon, threat);
                return;
            }
            MentalControlRuntime.updateGuardTarget(dragon, null);
            if (dragon.position().distanceToSqr(anchor.position()) <= arrivalRadiusSqr) {
                dragon.setDeltaMovement(dragon.getDeltaMovement().scale(0.25));
                setFlightTarget(dragon, dragon.position());
            } else {
                steerFlight(dragon, anchor.position());
                setFlightTarget(dragon, anchor.position());
                advanceFlight(dragon, anchor.position(), 0.10);
            }
        }

        private LivingEntity findThreat(ResolvedDestination anchor) {
            var point = anchor.position();
            return dragon.level().getEntitiesOfClass(
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
            if (candidate == null || candidate == dragon || candidate == controller
                    || !candidate.isAlive() || candidate.isRemoved()
                    || candidate.level() != dragon.level()
                    || candidate.position().distanceToSqr(anchor.position()) > detectionRadiusSqr
                    || TeamRelations.areAllied(controller, candidate)
                    || FriendlyFireSetting.shouldPrevent(controller, candidate)
                    || TeamRelations.areAllied(dragon, candidate)
                    || MentalPerceptionRuntime.decision(dragon, candidate)
                    == PerceptionDecision.HIDDEN) return false;

            var currentTarget = candidate instanceof Mob candidateMob ? candidateMob.getTarget() : null;
            var hostile = isProtected(currentTarget, anchor.entity());
            var lastVictim = candidate.getLastHurtMob();
            hostile |= candidate.tickCount - candidate.getLastHurtMobTimestamp() <= RECENT_HOSTILITY_TICKS
                    && isProtected(lastVictim, anchor.entity());
            return hostile;
        }

        private boolean isProtected(LivingEntity entity, LivingEntity anchorEntity) {
            return entity != null && (entity == anchorEntity || entity == controller
                    || TeamRelations.areAllied(controller, entity)
                    || FriendlyFireSetting.shouldPrevent(controller, entity));
        }

        private void clearThreat() {
            threat = null;
            MentalControlRuntime.updateGuardTarget(dragon, null);
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
            var phase = dragon.getPhaseManager().getCurrentPhase().getPhase();
            if (phase == EnderDragonPhase.HOVERING || phase == EnderDragonPhase.STRAFE_PLAYER) {
                dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
            }
        }
    }

    private record ResolvedDestination(Vec3 position, LivingEntity entity) {
    }
}
