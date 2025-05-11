package org.academy.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import org.academy.api.server.ability.AbilitySystemServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IntegratedServer.class)
public class MixinIntegratedServer {
    @Inject(method = "tickServer",at = @At("HEAD"))
    private void tickServer(CallbackInfo ci) {
        AbilitySystemServer.paused = Minecraft.getInstance().isPaused();
    }
}