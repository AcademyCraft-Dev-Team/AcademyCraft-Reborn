package org.academy.mixin.common;

import net.minecraft.world.entity.LivingEntity;
import org.academy.internal.server.time.TemporalMutationProtection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityTemporalOrientation {
    @Inject(method = {"setYHeadRot", "setYBodyRot"}, at = @At("HEAD"), cancellable = true)
    private void academy$protectTemporalBodyOrientation(float rotation, CallbackInfo ci) {
        if (TemporalMutationProtection.shouldBlock((LivingEntity) (Object) this)) ci.cancel();
    }
}
