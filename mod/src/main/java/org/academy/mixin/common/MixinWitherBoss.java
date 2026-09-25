package org.academy.mixin.common;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.entitycontrol.AttackDecision;
import org.academy.internal.common.ability.mentalout.control.DirectMobMovementAccess;
import org.academy.internal.common.ability.mentalout.control.MentalControlRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WitherBoss.class)
public abstract class MixinWitherBoss implements DirectMobMovementAccess {
    private static void clearDeniedHeads(WitherBoss wither, ServerLevel level) {
        for (var head = 0; head < 3; head++) {
            var entity = level.getEntity(wither.getAlternativeTarget(head));
            if (entity instanceof LivingEntity target
                    && MentalControlRuntime.attackDecision(wither, target) == AttackDecision.DENY) {
                wither.setAlternativeTarget(head, 0);
            }
        }
    }

    private static void clearHeads(WitherBoss wither) {
        for (var head = 0; head < 3; head++) wither.setAlternativeTarget(head, 0);
    }

    @Override
    public void academy$moveDirectly(Vec3 destination, double speedModifier) {
        var wither = (WitherBoss) (Object) this;
        wither.getNavigation().stop();
        wither.getMoveControl().setWantedPosition(
                destination.x, destination.y, destination.z, speedModifier);
        var delta = destination.subtract(wither.position());
        if (delta.lengthSqr() <= 1.0E-6) return;
        var desired = delta.normalize().scale(0.18 * speedModifier);
        wither.setDeltaMovement(wither.getDeltaMovement().scale(0.6).add(desired.scale(0.4)));
    }

    @Override
    public void academy$stopDirectMovement() {
        var wither = (WitherBoss) (Object) this;
        wither.getMoveControl().setWantedPosition(wither.getX(), wither.getY(), wither.getZ(), 0.0);
        wither.setDeltaMovement(wither.getDeltaMovement().scale(0.2));
    }

    @Inject(method = "customServerAiStep", at = @At("HEAD"))
    private void academy$filterMentalControlHeadsBeforeAttack(ServerLevel level, CallbackInfo ci) {
        clearDeniedHeads((WitherBoss) (Object) this, level);
    }

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void academy$prepareMentalStupor(CallbackInfo ci) {
        var wither = (WitherBoss) (Object) this;
        if (MentalControlRuntime.isFrozen(wither)) clearHeads(wither);
    }

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void academy$finishMentalStupor(CallbackInfo ci) {
        var wither = (WitherBoss) (Object) this;
        if (MentalControlRuntime.isFrozen(wither)) {
            clearHeads(wither);
            wither.setDeltaMovement(0.0, 0.0, 0.0);
        }
    }

    @Inject(method = "customServerAiStep", at = @At("TAIL"))
    private void academy$maintainMentalControlHeads(ServerLevel level, CallbackInfo ci) {
        var wither = (WitherBoss) (Object) this;
        if (MentalControlRuntime.isFrozen(wither)) {
            clearHeads(wither);
            return;
        }
        var forcedTarget = MentalControlRuntime.getForcedTarget(wither);
        var controlledTarget = forcedTarget != null
                ? forcedTarget
                : MentalControlRuntime.getGuardTarget(wither);
        if (controlledTarget != null) {
            for (var head = 0; head < 3; head++) wither.setAlternativeTarget(head, controlledTarget.getId());
            return;
        }
        clearDeniedHeads(wither, level);
    }
}
