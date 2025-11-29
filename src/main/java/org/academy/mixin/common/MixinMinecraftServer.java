package org.academy.mixin.common;

import net.minecraft.server.MinecraftServer;
import org.academy.api.server.ability.AbilitySystemServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServer {
    @Inject(method = "halt", at = @At("HEAD"))
    private void halt(boolean waitForServer, CallbackInfo ci) {
        if (AbilitySystemServer.scheduledFuture != null) {
            AbilitySystemServer.scheduledFuture.cancel(true);
        }
    }
}