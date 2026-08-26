package org.academy.internal.common.ability.mentalout.control;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.academy.api.common.entitycontrol.*;
import org.academy.internal.common.ability.mentalout.PlayerControlSessionManager;

import java.util.Optional;
import java.util.UUID;

/**
 * Adapts lease arbitration to a player input session instead of a mob AI controller.
 */
public final class ServerPlayerMentalControlAdapter implements MentalControlAdapter {
    @Override
    public boolean matches(LivingEntity subject) {
        return subject instanceof ServerPlayer;
    }

    @Override
    public ControlSupport support(LivingEntity subject, ControlCapability capability) {
        if (!matches(subject)) return ControlSupport.UNSUPPORTED;
        return switch (capability) {
            case FORCE_TARGET, FREEZE_AI, RELATION_CONTROL, PATH_CONTROL, VIEW_CONTROL, DIRECT_CONTROL ->
                    ControlSupport.FULL;
            case GUARD_CONTROL -> ControlSupport.UNSUPPORTED;
        };
    }

    @Override
    public ControlRejectionReason rejectionReason(LivingEntity subject, ControlCapability capability) {
        if (subject instanceof ServerPlayer player
                && PlayerControlSessionManager.isResistant(player)
                && capability != ControlCapability.RELATION_CONTROL) {
            return ControlRejectionReason.TEMPORARILY_UNAVAILABLE;
        }
        return MentalControlAdapter.super.rejectionReason(subject, capability);
    }

    @Override
    public ControlBinding activate(ControlContext context, ControlDirective directive) {
        if (!(context.subject() instanceof ServerPlayer subject)) {
            throw new IllegalArgumentException("Player adapter requires a ServerPlayer subject");
        }
        if (directive instanceof ControlDirective.MoveTo moveTo) {
            return PlayerNavigationRuntime.activate(context, subject, moveTo);
        }
        if (directive instanceof ControlDirective.ForceTarget(var targetUuid)) {
            return new PlayerForcedTargetBinding(context, subject, targetUuid);
        }
        if (directive instanceof ControlDirective.LookAt(var targetUuid) && context.controller() == subject) {
            return new SelfViewBinding(context, subject, targetUuid);
        }
        return ControlBinding.noop();
    }

    private static final class SelfViewBinding implements ControlBinding {
        private final ServerPlayer subject;
        private final UUID targetId;
        private final PlayerControlSessionManager.PathSessionToken session;
        private boolean complete;
        private boolean closed;
        private ControlFailureReason failure;

        private SelfViewBinding(ControlContext context, ServerPlayer subject, UUID targetId) {
            this.subject = subject;
            this.targetId = targetId;
            session = PlayerControlSessionManager.beginPath(context, subject);
        }

        @Override
        public void tick() {
            if (closed || complete || failure != null) return;
            if (PlayerControlSessionManager.isPathHandshakePending(session)) return;
            if (!PlayerControlSessionManager.isPathActive(session)) {
                var reason = PlayerControlSessionManager.consumePathEndReason(session).orElse(null);
                if (reason == PlayerControlSessionManager.EndReason.CONTROLLER_STOPPED) {
                    complete = true;
                } else {
                    failure = ControlFailureReason.CLIENT_TIMEOUT;
                }
                return;
            }
            var target = MentalControlRuntime.findLivingEntity(subject.level().getServer(), targetId);
            if (target == null || target.isRemoved() || !target.isAlive()
                    || target.level() != subject.level()) {
                failure = ControlFailureReason.TARGET_UNAVAILABLE;
                return;
            }
            var delta = target.getEyePosition().subtract(subject.getEyePosition());
            if (delta.lengthSqr() <= 1.0e-8) {
                complete = true;
                return;
            }
            var horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            var yaw = (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0f;
            var pitch = (float) -(Mth.atan2(delta.y, Math.max(horizontal, 1.0e-6)) * Mth.RAD_TO_DEG);
            PlayerControlSessionManager.submitPathFrame(session, new PlayerControlFrame(
                    0.0f, 0.0f, yaw, pitch, false, false, false, false, false,
                    movementMode(subject)
            ));
        }

        @Override
        public boolean isComplete() {
            return complete || failure != null;
        }

        @Override
        public Optional<ControlFailureReason> failureReason() {
            return Optional.ofNullable(failure);
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            PlayerControlSessionManager.submitPathFrame(session, PlayerControlFrame.NEUTRAL);
            PlayerControlSessionManager.closePath(session, false);
        }

        private static PlayerMovementMode movementMode(ServerPlayer player) {
            if (player.isPassenger()) return PlayerMovementMode.MOUNT;
            if (player.isFallFlying()) return PlayerMovementMode.GLIDE;
            if (player.getAbilities().flying) return PlayerMovementMode.FLY;
            if (player.isInWater()) return PlayerMovementMode.SWIM;
            if (player.onClimbable()) return PlayerMovementMode.CLIMB;
            return PlayerMovementMode.WALK;
        }
    }
}
