package org.academy.mixin.common.item;

import net.minecraft.world.entity.item.ItemEntity;
import org.academy.internal.common.world.item.DarkmatterItemUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemEntity.class)
public abstract class MixinItemEntityDarkmatterFireImmune {
    @Inject(method = "fireImmune", at = @At("RETURN"), cancellable = true)
    private void academy$darkmatterItemsIgnoreFire(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return;
        var stack = ((ItemEntity) (Object) this).getItem();
        if (DarkmatterItemUtil.hasFamilyEnchantment(stack)) cir.setReturnValue(true);
    }
}
