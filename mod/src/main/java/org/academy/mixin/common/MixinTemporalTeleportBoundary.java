package org.academy.mixin.common;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.internal.server.time.TemporalMutationProtection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinTemporalTeleportBoundary {
    @Inject(method = "teleport", at = @At("HEAD"), cancellable = true)
    private void academy$protectTemporalTeleport(CallbackInfo ci) {
        if (TemporalMutationProtection.shouldBlock(((ServerGamePacketListenerImpl) (Object) this).player)) ci.cancel();
    }
}
