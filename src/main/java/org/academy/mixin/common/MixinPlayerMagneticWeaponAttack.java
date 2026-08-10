package org.academy.mixin.common;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.academy.internal.common.ability.electromaster.skills.lv3.MagneticWeaponAttackContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Player.class)
public abstract class MixinPlayerMagneticWeaponAttack {
    @ModifyArgs(
            method = "attack",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
            )
    )
    private void academy$prepareMagneticWeaponDamage(Args args) {
        var player = (Player) (Object) this;
        var source = args.<DamageSource>get(0);
        var damage = args.<Float>get(1);
        args.set(1, MagneticWeaponAttackContext.prepareDamage(player, source, damage));
    }

    @Inject(method = "causeFoodExhaustion", at = @At("HEAD"), cancellable = true)
    private void academy$suppressMagneticWeaponExhaustion(float amount, CallbackInfo ci) {
        if (MagneticWeaponAttackContext.shouldSuppressExhaustion((Player) (Object) this)) {
            ci.cancel();
        }
    }
}
