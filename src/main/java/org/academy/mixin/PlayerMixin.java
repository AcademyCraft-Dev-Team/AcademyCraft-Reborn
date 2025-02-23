package org.academy.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class PlayerMixin {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    public void hurt(DamageSource damageSource, float f, CallbackInfoReturnable<Boolean> cir) {
        Player player = (Player) (Object) this;
        Entity source = damageSource.getEntity();
        Entity directEntity = damageSource.getDirectEntity();
        if (source != player) {
            if (source != null) {
                if (!damageSource.is(DamageTypes.THROWN)) {
                    source.hurt(damageSource, f);
                    cir.setReturnValue(false);
                }
            }
            if (directEntity != null) {
                if (damageSource.is(DamageTypes.THROWN)) {
                    directEntity.setDeltaMovement(directEntity.getDeltaMovement().scale(10));
                    cir.setReturnValue(false);
                }
            }
        }
        cir.setReturnValue(false);
    }
}
