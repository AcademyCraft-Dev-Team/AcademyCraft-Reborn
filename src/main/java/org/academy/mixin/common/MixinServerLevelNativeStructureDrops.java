package org.academy.mixin.common;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.academy.internal.common.structure.space.NativeStructureBreakDrops;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public abstract class MixinServerLevelNativeStructureDrops {
    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
    private void academy$captureNativeMiningItem(Entity entity, CallbackInfoReturnable<Boolean> callback) {
        if (NativeStructureBreakDrops.tryCapture((ServerLevel) (Object) this, entity)) callback.setReturnValue(true);
    }
}
