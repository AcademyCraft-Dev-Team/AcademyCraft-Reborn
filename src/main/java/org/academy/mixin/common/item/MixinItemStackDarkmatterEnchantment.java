package org.academy.mixin.common.item;

import net.minecraft.world.item.ItemStack;
import org.academy.internal.common.world.item.DarkmatterItemUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class MixinItemStackDarkmatterEnchantment {
    @Inject(method = "getMaxDamage", at = @At("RETURN"), cancellable = true)
    private void academy$doubleDarkmatterDurability(CallbackInfoReturnable<Integer> cir) {
        var original = cir.getReturnValueI();
        if (original <= 0) return;
        var stack = (ItemStack) (Object) this;
        if (!DarkmatterItemUtil.hasFamilyEnchantment(stack)) return;
        cir.setReturnValue((int) Math.min(Integer.MAX_VALUE, (long) original * 2L));
    }
}
