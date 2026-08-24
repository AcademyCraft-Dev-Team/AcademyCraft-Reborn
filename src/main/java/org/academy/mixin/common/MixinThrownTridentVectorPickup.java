package org.academy.mixin.common;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorProjectileRedirects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ThrownTrident.class)
public abstract class MixinThrownTridentVectorPickup {
    @ModifyExpressionValue(
            method = "playerTouch",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/projectile/arrow/ThrownTrident;ownedBy(Lnet/minecraft/world/entity/Entity;)Z"
            )
    )
    private boolean academy$allowOriginalOwnerPickup(
            boolean currentOwner,
            @Local(argsOnly = true) Player player
    ) {
        var trident = (ThrownTrident) (Object) this;
        return currentOwner || VectorProjectileRedirects.allowsOriginalOwnerPickup(trident, player);
    }
}
