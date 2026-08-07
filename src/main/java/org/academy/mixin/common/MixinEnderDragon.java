package org.academy.mixin.common;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.academy.api.common.entitycontrol.AttackDecision;
import org.academy.internal.common.ability.mentalout.control.MentalControlRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(EnderDragon.class)
public abstract class MixinEnderDragon {
    @Inject(method = "aiStep", at = @At("HEAD"), cancellable = true)
    private void academy$freezeMentalControlAi(CallbackInfo ci) {
        var dragon = (EnderDragon) (Object) this;
        if (!MentalControlRuntime.isFrozen(dragon)) return;
        if (dragon.isDeadOrDying() || dragon.getHealth() <= 0.0F) return;
        dragon.setDeltaMovement(0.0, 0.0, 0.0);
        ci.cancel();
    }

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void academy$reassertMentalViewAfterDragonPhase(CallbackInfo ci) {
        MentalControlRuntime.beforeLookControlTick((EnderDragon) (Object) this);
    }

    @Inject(method = "canAttack", at = @At("HEAD"), cancellable = true)
    private void academy$applyMentalControlRelation(
            LivingEntity target,
            CallbackInfoReturnable<Boolean> cir
    ) {
        var decision = MentalControlRuntime.attackDecision((EnderDragon) (Object) this, target);
        if (decision == AttackDecision.ALLOW) cir.setReturnValue(true);
        if (decision == AttackDecision.DENY) cir.setReturnValue(false);
    }

    @ModifyVariable(
            method = "knockBack(Lnet/minecraft/server/level/ServerLevel;Ljava/util/List;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private List<Entity> academy$filterMentalControlWingTargets(List<Entity> entities) {
        return filterDeniedTargets(entities);
    }

    @ModifyVariable(
            method = "hurt(Lnet/minecraft/server/level/ServerLevel;Ljava/util/List;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private List<Entity> academy$filterMentalControlHeadTargets(List<Entity> entities) {
        return filterDeniedTargets(entities);
    }

    private List<Entity> filterDeniedTargets(List<Entity> entities) {
        var dragon = (EnderDragon) (Object) this;
        return entities.stream()
                .filter(entity -> !(entity instanceof LivingEntity target)
                        || MentalControlRuntime.attackDecision(dragon, target) != AttackDecision.DENY)
                .toList();
    }
}
