package org.academy.mixin.common;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.academy.api.common.entitycontrol.AttackDecision;
import org.academy.internal.common.ability.mentalout.control.MentalControlMobAccess;
import org.academy.internal.common.ability.mentalout.control.MentalControlRuntime;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class MixinMob implements MentalControlMobAccess {
    @Shadow
    private @Nullable LivingEntity target;

    @Override
    public @Nullable LivingEntity academy$getRawMentalControlTarget() {
        return target;
    }

    @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
    private void academy$applyMentalControlBeforeAi(CallbackInfo ci) {
        var mob = (Mob) (Object) this;
        if (MentalControlRuntime.isFrozen(mob)) {
            var verticalVelocity = Math.min(0.0, mob.getDeltaMovement().y);
            mob.stopInPlace();
            mob.setJumping(false);
            mob.setDeltaMovement(0.0, verticalVelocity, 0.0);
            ci.cancel();
            return;
        }
        academy$maintainForcedTarget(mob);
    }

    @Inject(method = "serverAiStep", at = @At("TAIL"))
    private void academy$applyMentalControlAfterAi(CallbackInfo ci) {
        var mob = (Mob) (Object) this;
        MentalControlRuntime.enforceTargetWhitelist(mob);
        academy$maintainForcedTarget(mob);
    }

    @Inject(method = "canAttack", at = @At("HEAD"), cancellable = true)
    private void academy$allowForcedMentalTarget(
            LivingEntity target,
            CallbackInfoReturnable<Boolean> cir
    ) {
        var decision = MentalControlRuntime.attackDecision((Mob) (Object) this, target);
        if (decision == AttackDecision.ALLOW) cir.setReturnValue(true);
        if (decision == AttackDecision.DENY) cir.setReturnValue(false);
    }

    @Inject(method = "asValidTarget", at = @At("HEAD"), cancellable = true)
    private void academy$acceptForcedMentalTarget(
            LivingEntity target,
            CallbackInfoReturnable<LivingEntity> cir
    ) {
        if (target == null) return;
        var decision = MentalControlRuntime.attackDecision((Mob) (Object) this, target);
        if (decision == AttackDecision.ALLOW) cir.setReturnValue(target);
        if (decision == AttackDecision.DENY) cir.setReturnValue(null);
    }

    @Inject(method = "getTarget", at = @At("HEAD"), cancellable = true)
    private void academy$getForcedMentalTarget(CallbackInfoReturnable<LivingEntity> cir) {
        var target = MentalControlRuntime.getForcedTarget((Mob) (Object) this);
        if (target != null) {
            cir.setReturnValue(target);
        }
    }

    @Inject(method = "getTargetUnchecked", at = @At("HEAD"), cancellable = true)
    private void academy$getUncheckedForcedMentalTarget(CallbackInfoReturnable<LivingEntity> cir) {
        var target = MentalControlRuntime.getForcedTarget((Mob) (Object) this);
        if (target != null) {
            cir.setReturnValue(target);
        }
    }

    private static void academy$maintainForcedTarget(Mob mob) {
        var target = MentalControlRuntime.getForcedTarget(mob);
        if (target == null || !target.isAlive() || target.level() != mob.level()) return;

        MentalControlRuntime.maintainTarget(mob);
        mob.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
    }
}
