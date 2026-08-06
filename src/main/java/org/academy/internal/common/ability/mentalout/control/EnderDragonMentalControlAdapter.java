package org.academy.internal.common.ability.mentalout.control;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import org.academy.api.common.entitycontrol.ControlBinding;
import org.academy.api.common.entitycontrol.ControlCapability;
import org.academy.api.common.entitycontrol.ControlContext;
import org.academy.api.common.entitycontrol.ControlDirective;
import org.academy.api.common.entitycontrol.ControlSupport;
import org.academy.api.common.entitycontrol.MentalControlAdapter;

import java.util.UUID;

public final class EnderDragonMentalControlAdapter implements MentalControlAdapter {
    @Override
    public boolean matches(LivingEntity subject) {
        return subject instanceof EnderDragon;
    }

    @Override
    public ControlSupport support(LivingEntity subject, ControlCapability capability) {
        if (!matches(subject)) return ControlSupport.UNSUPPORTED;
        return capability == ControlCapability.PATH_CONTROL || capability == ControlCapability.VIEW_CONTROL
                ? ControlSupport.UNSUPPORTED
                : ControlSupport.FULL;
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
            case ControlDirective.MoveTo ignored -> throw new IllegalArgumentException(
                    "Ender Dragon does not support path control");
            case ControlDirective.LookAt ignored -> throw new IllegalArgumentException(
                    "Ender Dragon does not support view control");
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
            var manager = dragon.getPhaseManager();
            if (manager.getCurrentPhase().getPhase() != EnderDragonPhase.STRAFE_PLAYER) {
                manager.setPhase(EnderDragonPhase.STRAFE_PLAYER);
            }
            manager.getPhase(EnderDragonPhase.STRAFE_PLAYER).setTarget(target);
        }

        @Override
        public void close() {
            var manager = dragon.getPhaseManager();
            if (MentalControlRuntime.getForcedTarget(dragon) == null
                    && manager.getCurrentPhase().getPhase() == EnderDragonPhase.STRAFE_PLAYER) {
                manager.setPhase(EnderDragonPhase.HOLDING_PATTERN);
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
                    || dragon.getPhaseManager().getCurrentPhase().getPhase() == EnderDragonPhase.DYING) {
                return;
            }
            dragon.setDeltaMovement(0.0, 0.0, 0.0);
            var manager = dragon.getPhaseManager();
            if (manager.getCurrentPhase().getPhase() != EnderDragonPhase.HOVERING) {
                manager.setPhase(EnderDragonPhase.HOVERING);
            }
        }

        @Override
        public void close() {
            var manager = dragon.getPhaseManager();
            if (!MentalControlRuntime.isFrozen(dragon)
                    && manager.getCurrentPhase().getPhase() == EnderDragonPhase.HOVERING) {
                manager.setPhase(EnderDragonPhase.HOLDING_PATTERN);
            }
        }
    }
}
