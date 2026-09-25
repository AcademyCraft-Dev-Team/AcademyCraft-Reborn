package org.academy.mixin.common;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import net.minecraft.world.entity.player.Player;
import org.academy.api.common.entitycontrol.AttackDecision;
import org.academy.internal.common.ability.mentalout.control.MentalControlRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractCubeMob.class)
public abstract class MixinAbstractCubeMob {
    // 26.3 renamed AbstractCubeMob#isDealsDamage to canDealDamage.
    @Shadow
    protected abstract boolean canDealDamage();

    @Shadow
    protected abstract void dealDamage(LivingEntity target);

    @Inject(method = "push", at = @At("TAIL"))
    private void academy$damageAuthorizedMentalControlTarget(Entity entity, CallbackInfo ci) {
        if (!canDealDamage() || !(entity instanceof LivingEntity target)
                || entity instanceof Player || entity instanceof IronGolem) return;
        var cubeMob = (AbstractCubeMob) (Object) this;
        if (MentalControlRuntime.attackDecision(cubeMob, target) == AttackDecision.ALLOW) {
            dealDamage(target);
        }
    }
}
