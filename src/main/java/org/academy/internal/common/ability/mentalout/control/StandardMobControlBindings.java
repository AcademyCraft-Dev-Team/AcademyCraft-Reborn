package org.academy.internal.common.ability.mentalout.control;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.academy.api.common.entitycontrol.ControlBinding;
import org.academy.api.common.entitycontrol.ControlCapability;
import org.academy.api.common.entitycontrol.ControlDirective;

import java.util.UUID;

final class StandardMobControlBindings {
    private StandardMobControlBindings() {
    }

    static ControlBinding create(Mob mob, ControlDirective directive) {
        return switch (directive) {
            case ControlDirective.ForceTarget forceTarget -> new ForceTargetBinding(mob, forceTarget.targetUuid());
            case ControlDirective.FreezeAi ignored -> new FreezeBinding(mob);
            case ControlDirective.ImpressionAlliance ignored -> new RelationBinding(mob);
            case ControlDirective.MoveTo moveTo -> new PathBinding(mob, moveTo.targetUuid());
            case ControlDirective.LookAt lookAt -> new LookBinding(mob, lookAt.targetUuid());
        };
    }

    private static final class ForceTargetBinding implements ControlBinding {
        private final Mob mob;
        private final UUID targetId;

        private ForceTargetBinding(Mob mob, UUID targetId) {
            this.mob = mob;
            this.targetId = targetId;
        }

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

    private static final class FreezeBinding implements ControlBinding {
        private final Mob mob;

        private FreezeBinding(Mob mob) {
            this.mob = mob;
        }

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

    private static final class RelationBinding implements ControlBinding {
        private final Mob mob;

        private RelationBinding(Mob mob) {
            this.mob = mob;
        }

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
        private final UUID targetId;

        private PathBinding(Mob mob, UUID targetId) {
            this.mob = mob;
            this.targetId = targetId;
        }

        @Override
        public void tick() {
            if (!mob.isAlive() || mob.isRemoved()) return;
            var target = MentalControlRuntime.findLivingEntity(mob.level().getServer(), targetId);
            if (target == null || target.level() != mob.level() || !target.isAlive() || target.isRemoved()) return;
            var navigation = mob.getNavigation();
            var path = navigation.getPath();
            if (path == null || navigation.isDone() || !path.getTarget().equals(target.blockPosition())) {
                navigation.moveTo(target, 1.0);
            }
        }

        @Override
        public void close() {
            if (MentalControlRuntime.effectiveDirective(mob, ControlCapability.PATH_CONTROL).isEmpty()) {
                mob.getNavigation().stop();
            }
        }
    }

    private static final class LookBinding implements ControlBinding {
        private final Mob mob;
        private final UUID targetId;

        private LookBinding(Mob mob, UUID targetId) {
            this.mob = mob;
            this.targetId = targetId;
        }

        @Override
        public void tick() {
            if (!mob.isAlive() || mob.isRemoved()) return;
            var target = MentalControlRuntime.findLivingEntity(mob.level().getServer(), targetId);
            if (target == null || target.level() != mob.level() || !target.isAlive() || target.isRemoved()) return;
            mob.getLookControl().setLookAt(target, 30.0f, 30.0f);
        }

        @Override
        public void close() {
        }
    }
}
