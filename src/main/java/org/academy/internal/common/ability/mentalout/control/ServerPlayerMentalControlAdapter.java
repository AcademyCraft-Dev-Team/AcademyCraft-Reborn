package org.academy.internal.common.ability.mentalout.control;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.academy.api.common.entitycontrol.ControlBinding;
import org.academy.api.common.entitycontrol.ControlCapability;
import org.academy.api.common.entitycontrol.ControlContext;
import org.academy.api.common.entitycontrol.ControlDirective;
import org.academy.api.common.entitycontrol.ControlSupport;
import org.academy.api.common.entitycontrol.ControlRejectionReason;
import org.academy.api.common.entitycontrol.MentalControlAdapter;
import org.academy.internal.common.ability.mentalout.PlayerControlSessionManager;

/** Adapts lease arbitration to a player input session instead of a mob AI controller. */
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
        if (directive instanceof ControlDirective.ForceTarget forceTarget) {
            return new PlayerForcedTargetBinding(context, subject, forceTarget.targetUuid());
        }
        return ControlBinding.noop();
    }
}
