package org.academy.internal.common.ability.mentalout.control;

import net.minecraft.util.Unit;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.warden.WardenAi;
import org.academy.api.common.entitycontrol.*;

import java.util.UUID;

public final class WardenMentalControlAdapter implements MentalControlAdapter {
    @Override
    public boolean matches(LivingEntity subject) {
        return subject instanceof Warden;
    }

    @Override
    public ControlSupport support(LivingEntity subject, ControlCapability capability) {
        if (!matches(subject)) return ControlSupport.UNSUPPORTED;
        return ControlSupport.FULL;
    }

    @Override
    public ControlBinding activate(ControlContext context, ControlDirective directive) {
        if (!(context.subject() instanceof Warden warden)) {
            throw new IllegalArgumentException("Warden adapter requires a Warden subject");
        }
        var delegate = StandardMobControlBindings.create(context, warden, directive);
        return switch (directive) {
            case ControlDirective.ForceTarget forceTarget ->
                    new ForceTargetBinding(warden, forceTarget.targetUuid(), delegate);
            case ControlDirective.ImpressionAlliance ignored ->
                    new RelationBinding(warden, delegate);
            default -> delegate;
        };
    }

    /**
     * Warden combat has extra activity gates which ordinary mobs do not have. A raw
     * {@code ATTACK_TARGET} memory is insufficient while emerge, roar, sniff or dig is active;
     * some of those activities can otherwise delay a commanded attack for more than six seconds.
     */
    private static final class ForceTargetBinding implements ControlBinding {
        private final Warden warden;
        private final UUID targetId;
        private final ControlBinding delegate;
        private boolean firstTick = true;

        private ForceTargetBinding(Warden warden, UUID targetId, ControlBinding delegate) {
            this.warden = warden;
            this.targetId = targetId;
            this.delegate = delegate;
        }

        @Override
        public void tick() {
            delegate.tick();
            var target = MentalControlRuntime.getForcedTarget(warden);
            if (target == null || !target.getUUID().equals(targetId)) return;

            cancelTransientActivity(warden, firstTick);
            firstTick = false;

            // Reassert after cancelling transitional activities so the next Brain tick enters
            // FIGHT directly instead of waiting for a vanilla anger/roar transition.
            MentalControlRuntime.maintainTarget(warden);
        }

        @Override
        public void beforeNavigationTick() {
            delegate.beforeNavigationTick();
        }

        @Override
        public void beforeMoveControlTick() {
            delegate.beforeMoveControlTick();
        }

        @Override
        public void beforeLookControlTick() {
            delegate.beforeLookControlTick();
        }

        @Override
        public void close() {
            delegate.close();
        }
    }

    private record RelationBinding(Warden warden, ControlBinding delegate) implements ControlBinding {
        @Override
        public void tick() {
            delegate.tick();
            if (MentalControlRuntime.suppressesAutonomousCombat(warden)) {
                cancelTransientActivity(warden, false);
            }
        }

        @Override
        public void close() {
            delegate.close();
        }
    }

    private static void cancelTransientActivity(Warden warden, boolean resetCurrentIntent) {
        var brain = warden.getBrain();
        var wasTransitioning = warden.hasPose(Pose.EMERGING)
                || warden.hasPose(Pose.DIGGING)
                || warden.hasPose(Pose.ROARING)
                || warden.hasPose(Pose.SNIFFING)
                || brain.hasMemoryValue(MemoryModuleType.IS_EMERGING)
                || brain.hasMemoryValue(MemoryModuleType.ROAR_TARGET)
                || brain.hasMemoryValue(MemoryModuleType.IS_SNIFFING);

        brain.eraseMemory(MemoryModuleType.IS_EMERGING);
        brain.eraseMemory(MemoryModuleType.ROAR_TARGET);
        brain.eraseMemory(MemoryModuleType.ROAR_SOUND_DELAY);
        brain.eraseMemory(MemoryModuleType.ROAR_SOUND_COOLDOWN);
        brain.eraseMemory(MemoryModuleType.IS_SNIFFING);
        brain.eraseMemory(MemoryModuleType.DISTURBANCE_LOCATION);
        brain.setMemoryWithExpiry(MemoryModuleType.DIG_COOLDOWN, Unit.INSTANCE, 1200L);

        if (resetCurrentIntent || wasTransitioning) {
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
            brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
            warden.getNavigation().stop();
            var verticalVelocity = Math.min(0.0, warden.getDeltaMovement().y);
            warden.stopInPlace();
            warden.setDeltaMovement(0.0, verticalVelocity, 0.0);
        }
        if (wasTransitioning) warden.setPose(Pose.STANDING);
        if (resetCurrentIntent || wasTransitioning) WardenAi.updateActivity(brain);
    }
}
