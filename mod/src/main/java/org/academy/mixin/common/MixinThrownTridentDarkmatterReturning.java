package org.academy.mixin.common;

import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import org.academy.api.common.ability.darkmatter.DarkmatterModifiers;
import org.academy.internal.common.world.item.DarkmatterItemUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ThrownTrident.class)
public abstract class MixinThrownTridentDarkmatterReturning {
    @Inject(method = "getLoyaltyFromItem", at = @At("RETURN"), cancellable = true)
    private void academy$applyDarkmatterReturning(
            ItemStack stack,
            CallbackInfoReturnable<Byte> callback
    ) {
        if (DarkmatterItemUtil.modifierLevel(stack, DarkmatterModifiers.RETURNING) > 0) {
            callback.setReturnValue((byte) Math.max(1, callback.getReturnValue()));
        }
    }
}
