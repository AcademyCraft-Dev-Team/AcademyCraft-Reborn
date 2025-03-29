package org.academy.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.academy.internal.common.ability.builtin.accelerator.skills.VectorManipulation;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class PlayerMixin {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    public void hurt(DamageSource damageSource, float amount, CallbackInfoReturnable<Boolean> cir) {
        Pair<Boolean,Float> pair=VectorManipulation.Server.handleHurt((Player) (Object) this, damageSource, amount);
        if (!pair.getLeft()) {
            cir.setReturnValue(false);
        } else {
            amount = pair.getRight();
        }
    }
}