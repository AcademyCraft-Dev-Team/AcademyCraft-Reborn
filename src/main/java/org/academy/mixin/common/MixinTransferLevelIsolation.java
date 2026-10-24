package org.academy.mixin.common;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.academy.internal.common.structure.transaction.PayloadIsolation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class MixinTransferLevelIsolation {
    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void academy$beforeDrops(BlockPos pos, boolean drop, Entity entity, int limit, CallbackInfoReturnable<Boolean> result) {
        if (PayloadIsolation.denied((Level) (Object) this, pos)) result.setReturnValue(false);
    }
}
