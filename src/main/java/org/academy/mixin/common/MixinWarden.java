package org.academy.mixin.common;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.warden.AngerLevel;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import org.academy.api.common.entitycontrol.AttackDecision;
import org.academy.internal.common.ability.mentalout.control.MentalControlRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Warden.class)
public abstract class MixinWarden {
    @Inject(method = "increaseAngerAt(Lnet/minecraft/world/entity/Entity;IZ)V", at = @At("HEAD"), cancellable = true)
    private void academy$restrictMentalControlAnger(
            Entity target,
            int amount,
            boolean playSound,
            CallbackInfo ci
    ) {
        if (!MentalControlRuntime.isHostilityAllowed((Warden) (Object) this, target)) {
            ci.cancel();
        }
    }

    @Inject(method = "setAttackTarget", at = @At("HEAD"), cancellable = true)
    private void academy$restrictMentalControlBrainTarget(LivingEntity target, CallbackInfo ci) {
        if (target != null
                && MentalControlRuntime.attackDecision((Warden) (Object) this, target) == AttackDecision.DENY) {
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void academy$clearUnauthorizedMentalControlAnger(CallbackInfo ci) {
        var warden = (Warden) (Object) this;
        warden.getAngerManagement().getActiveEntity().ifPresent(active -> {
            if (!MentalControlRuntime.isHostilityAllowed(warden, active)) {
                warden.clearAnger(active);
            }
        });
        var roarTarget = warden.getBrain().getMemoryInternal(MemoryModuleType.ROAR_TARGET);
        if (roarTarget != null && roarTarget.isPresent()
                && !MentalControlRuntime.isHostilityAllowed(warden, roarTarget.get())) {
            warden.getBrain().eraseMemory(MemoryModuleType.ROAR_TARGET);
        }
    }

    @Inject(method = "getTarget", at = @At("HEAD"), cancellable = true)
    private void academy$getMentalControlTarget(CallbackInfoReturnable<LivingEntity> cir) {
        var target = MentalControlRuntime.getForcedTarget((Warden) (Object) this);
        if (target != null) cir.setReturnValue(target);
    }

    @Inject(method = "canTargetEntity", at = @At("HEAD"), cancellable = true)
    private void academy$applyMentalControlRelation(
            Entity target,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!(target instanceof LivingEntity living)) return;
        var decision = MentalControlRuntime.attackDecision((Warden) (Object) this, living);
        if (decision == AttackDecision.ALLOW) cir.setReturnValue(true);
        if (decision == AttackDecision.DENY) cir.setReturnValue(false);
    }

    @Inject(method = "getAngerLevel", at = @At("HEAD"), cancellable = true)
    private void academy$forceMentalControlAnger(CallbackInfoReturnable<AngerLevel> cir) {
        var warden = (Warden) (Object) this;
        if (MentalControlRuntime.getForcedTarget(warden) != null) {
            cir.setReturnValue(AngerLevel.ANGRY);
            return;
        }
        var active = warden.getAngerManagement().getActiveEntity();
        if (active.isPresent()) {
            if (!MentalControlRuntime.isHostilityAllowed(warden, active.get())) {
                cir.setReturnValue(AngerLevel.CALM);
            }
        } else if (!MentalControlRuntime.isHostilityAllowed(warden, null)) {
            cir.setReturnValue(AngerLevel.CALM);
        }
    }

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/gameevent/vibrations/VibrationSystem$Ticker;tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/gameevent/vibrations/VibrationSystem$Data;Lnet/minecraft/world/level/gameevent/vibrations/VibrationSystem$User;)V"
            )
    )
    private void academy$suppressMentalStuporVibrations(
            Level level,
            VibrationSystem.Data data,
            VibrationSystem.User user
    ) {
        if (!MentalControlRuntime.isFrozen((Warden) (Object) this)) {
            VibrationSystem.Ticker.tick(level, data, user);
        }
    }
}
