package org.academy.internal.common.ability.mentalout.control;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import org.academy.api.common.entitycontrol.AttackDecision;
import org.academy.api.common.entitycontrol.ControlBinding;
import org.academy.api.common.entitycontrol.ControlCapability;
import org.academy.api.common.entitycontrol.ControlContext;
import org.academy.api.common.entitycontrol.ControlDirective;
import org.academy.api.common.entitycontrol.ControlRejectionReason;
import org.academy.api.common.entitycontrol.ControlSupport;
import org.academy.api.common.entitycontrol.MentalControlAdapter;

import java.util.UUID;

public final class WitherMentalControlAdapter implements MentalControlAdapter {
    @Override
    public boolean matches(LivingEntity subject) {
        return subject instanceof WitherBoss;
    }

    @Override
    public ControlSupport support(LivingEntity subject, ControlCapability capability) {
        if (!(subject instanceof WitherBoss wither)) return ControlSupport.UNSUPPORTED;
        if (capability == ControlCapability.FREEZE_AI && wither.getInvulnerableTicks() > 0) {
            return ControlSupport.UNSUPPORTED;
        }
        return ControlSupport.FULL;
    }

    @Override
    public ControlRejectionReason rejectionReason(
            LivingEntity subject,
            ControlCapability capability
    ) {
        if (subject instanceof WitherBoss wither
                && capability == ControlCapability.FREEZE_AI
                && wither.getInvulnerableTicks() > 0) {
            return ControlRejectionReason.TEMPORARILY_UNAVAILABLE;
        }
        return MentalControlAdapter.super.rejectionReason(subject, capability);
    }

    @Override
    public ControlBinding activate(ControlContext context, ControlDirective directive) {
        if (!(context.subject() instanceof WitherBoss wither)) {
            throw new IllegalArgumentException("Wither adapter requires a Wither subject");
        }
        return switch (directive) {
            case ControlDirective.ForceTarget forceTarget -> new ForceTargetBinding(wither, forceTarget.targetUuid());
            case ControlDirective.FreezeAi ignored -> new FreezeBinding(wither);
            case ControlDirective.ImpressionAlliance ignored -> new RelationBinding(wither);
        };
    }

    private static void clearHeads(WitherBoss wither) {
        for (var head = 0; head < 3; head++) wither.setAlternativeTarget(head, 0);
    }

    private static final class ForceTargetBinding implements ControlBinding {
        private final WitherBoss wither;
        private final UUID targetId;
        private int lastEntityId = -1;

        private ForceTargetBinding(WitherBoss wither, UUID targetId) {
            this.wither = wither;
            this.targetId = targetId;
        }

        @Override
        public void tick() {
            if (MentalControlRuntime.isFrozen(wither)) {
                clearHeads(wither);
                return;
            }
            var target = MentalControlRuntime.findLivingEntity(wither.level().getServer(), targetId);
            if (target == null || target.level() != wither.level() || !target.isAlive() || target.isRemoved()) return;
            MentalControlRuntime.maintainTarget(wither);
            lastEntityId = target.getId();
            for (var head = 0; head < 3; head++) wither.setAlternativeTarget(head, target.getId());
        }

        @Override
        public void close() {
            for (var head = 0; head < 3; head++) {
                if (wither.getAlternativeTarget(head) == lastEntityId) wither.setAlternativeTarget(head, 0);
            }
        }
    }

    private static final class FreezeBinding implements ControlBinding {
        private final WitherBoss wither;

        private FreezeBinding(WitherBoss wither) {
            if (wither.getInvulnerableTicks() > 0) {
                throw new IllegalStateException("Wither is still in its spawn invulnerability phase");
            }
            this.wither = wither;
        }

        @Override
        public void tick() {
            clearHeads(wither);
            wither.stopInPlace();
            wither.getNavigation().stop();
            wither.setJumping(false);
            wither.setDeltaMovement(0.0, 0.0, 0.0);
        }

        @Override
        public void close() {
        }
    }

    private static final class RelationBinding implements ControlBinding {
        private final WitherBoss wither;

        private RelationBinding(WitherBoss wither) {
            this.wither = wither;
        }

        @Override
        public void tick() {
            MentalControlRuntime.enforceTargetWhitelist(wither);
            for (var head = 0; head < 3; head++) {
                var target = wither.level().getEntity(wither.getAlternativeTarget(head));
                if (target instanceof LivingEntity living
                        && MentalControlRuntime.attackDecision(wither, living) == AttackDecision.DENY) {
                    wither.setAlternativeTarget(head, 0);
                }
            }
        }

        @Override
        public void close() {
        }
    }
}
