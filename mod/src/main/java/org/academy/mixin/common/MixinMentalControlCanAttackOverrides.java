package org.academy.mixin.common;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.dolphin.Dolphin;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import org.academy.api.common.entitycontrol.AttackDecision;
import org.academy.internal.common.ability.mentalout.control.MentalControlRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({TamableAnimal.class, Dolphin.class, IronGolem.class, Breeze.class, AbstractIllager.class})
public abstract class MixinMentalControlCanAttackOverrides {
    @Inject(method = "canAttack", at = @At("HEAD"), cancellable = true)
    private void academy$allowForcedMentalTarget(
            LivingEntity target,
            CallbackInfoReturnable<Boolean> cir
    ) {
        var decision = MentalControlRuntime.attackDecision((LivingEntity) (Object) this, target);
        if (decision == AttackDecision.ALLOW) cir.setReturnValue(true);
        if (decision == AttackDecision.DENY) cir.setReturnValue(false);
    }
}
