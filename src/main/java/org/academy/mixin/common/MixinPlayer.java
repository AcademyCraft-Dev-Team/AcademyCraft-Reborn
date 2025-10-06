package org.academy.mixin.common;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.academy.internal.common.ability.accelerator.skills.VectorReflection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class MixinPlayer {
    @SuppressWarnings("UnusedAssignment")
    @Inject(method = "hurtServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Avatar;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"), cancellable = true)
    public void hurt(ServerLevel serverLevel, DamageSource damageSource, float amount, CallbackInfoReturnable<Boolean> cir) {
        var pair = VectorReflection.Server.onPlayerHurt((Player) (Object) this, damageSource, amount);
        if (!pair.getLeft()) {
            cir.setReturnValue(false);
        } else {
            amount = pair.getRight();
        }
    }
}