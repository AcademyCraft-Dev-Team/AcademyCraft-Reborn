package org.academy.internal.common.ability.mentalout.control;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.entitycontrol.ControlBinding;
import org.academy.api.common.entitycontrol.ControlCapability;
import org.academy.api.common.entitycontrol.ControlContext;
import org.academy.api.common.entitycontrol.ControlDestination;
import org.academy.api.common.entitycontrol.ControlDirective;
import org.academy.api.common.entitycontrol.ControlFailureReason;
import org.academy.api.common.entitycontrol.ControlSupport;
import org.academy.api.common.entitycontrol.MentalControlAdapter;
import org.academy.internal.common.world.damagesource.FriendlyFireSetting;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

public final class EnderDragonMentalControlAdapter implements MentalControlAdapter {
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
            case ControlDirective.ForceTarget forceTarget -> new ForceTargetBinding(dragon, forceTarget.targetUuid());
            case ControlDirective.FreezeAi ignored -> new FreezeBinding(dragon);
            case ControlDirective.ImpressionAlliance ignored -> ControlBinding.noop();
            case ControlDirective.MoveTo moveTo -> new MoveBinding(
                    dragon, moveTo.destination(), moveTo.arrivalRadius());
            case ControlDirective.LookAt lookAt -> new LookBinding(dragon, lookAt.targetUuid());
            case ControlDirective.Guard guard -> new GuardBinding(
                    dragon,
                    context.controller(),
                    guard.destination(),
                    guard.detectionRadius(),
                    guard.arrivalRadius()
            );
        };
    }

    private static final class ForceTargetBinding implements ControlBinding {
        private final EnderDragon dragon;
        private final UUID targetId;

        private ForceTargetBinding(EnderDragon dragon, UUID targetId) {
            this.dragon = dragon;
            this.targetId = targetId;
        }

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

    private static final class FreezeBinding implements ControlBinding {
        private final EnderDragon dragon;

        private FreezeBinding(EnderDragon dragon) {
            this.dragon = dragon;
        }

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
        private final net.minecraft.server.level.ServerPlayer controller;
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
                net.minecraft.server.level.ServerPlayer controller,
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
                    || controller.isAlliedTo(candidate)
                    || FriendlyFireSetting.shouldPrevent(controller, candidate)
                    || dragon.isAlliedTo(candidate)
                    || MentalPerceptionRuntime.decision(dragon, candidate)
                    == org.academy.api.common.entitycontrol.PerceptionDecision.HIDDEN) return false;

            var currentTarget = candidate instanceof Mob candidateMob ? candidateMob.getTarget() : null;
            var hostile = isProtected(currentTarget, anchor.entity());
            var lastVictim = candidate.getLastHurtMob();
            hostile |= lastVictim != null
                    && candidate.tickCount - candidate.getLastHurtMobTimestamp() <= RECENT_HOSTILITY_TICKS
                    && isProtected(lastVictim, anchor.entity());
            return hostile;
        }

        private boolean isProtected(LivingEntity entity, LivingEntity anchorEntity) {
            return entity != null && (entity == anchorEntity || entity == controller
                    || controller.isAlliedTo(entity)
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
        var horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        dragon.setYRot((float) (Math.atan2(delta.z, delta.x) * 180.0 / Math.PI) - 90.0F);
        dragon.yHeadRot = dragon.getYRot();
        dragon.setXRot((float) -(Math.atan2(delta.y, horizontal) * 180.0 / Math.PI));
    }

    private static void steerFlight(EnderDragon dragon, Vec3 target) {
        var delta = target.subtract(dragon.position());
        if (delta.x * delta.x + delta.z * delta.z <= 1.0E-6) return;
        dragon.setYRot(net.minecraft.util.Mth.wrapDegrees(
                180.0F - (float) Math.toDegrees(Math.atan2(delta.x, delta.z))));
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

    private record ResolvedDestination(Vec3 position, LivingEntity entity) {
    }
}
