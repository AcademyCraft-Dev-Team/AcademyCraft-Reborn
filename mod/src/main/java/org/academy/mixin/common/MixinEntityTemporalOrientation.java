package org.academy.mixin.common;

import net.minecraft.world.entity.Entity;
import org.academy.internal.server.time.TemporalMutationProtection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class MixinEntityTemporalOrientation {
    @Inject(method = {"setYRot", "setXRot"}, at = @At("HEAD"), cancellable = true)
    private void academy$protectTemporalOrientation(float rotation, CallbackInfo ci) {
        if (TemporalMutationProtection.shouldBlock((Entity) (Object) this)) ci.cancel();
    }
}
