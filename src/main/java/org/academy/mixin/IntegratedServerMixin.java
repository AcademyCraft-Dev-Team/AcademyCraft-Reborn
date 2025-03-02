package org.academy.mixin;

import net.minecraft.client.server.IntegratedServer;
import org.academy.AbilitySystemServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(IntegratedServer.class)
public class IntegratedServerMixin {
    @Shadow
    private boolean paused;

    @Inject(method = "tickServer", at = @At("HEAD"))
    public void tickServer(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        AbilitySystemServer.paused = this.paused;
    }
}
