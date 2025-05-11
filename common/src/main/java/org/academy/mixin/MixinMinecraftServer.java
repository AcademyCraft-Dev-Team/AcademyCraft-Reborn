package org.academy.mixin;

import net.minecraft.server.MinecraftServer;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftServer;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.tick.ServerTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public class MixinMinecraftServer {
    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;buildServerStatus()Lnet/minecraft/network/protocol/status/ServerStatus;", ordinal = 0), method = "runServer")
    private void init(CallbackInfo info) {
        AcademyCraftServer.init((MinecraftServer) (Object) this);
    }

    @Inject(method = "tickServer", at = @At("HEAD"))
    private void tickServer(CallbackInfo ci) {
        ServerTickEvent event = new ServerTickEvent();
        AcademyCraft.EVENT_BUS.post(event);
        if (event.isCanceled()) return;
        AbilitySystemServer.MinecraftServerThread.tickMinecraftServerThread((MinecraftServer) (Object) this);
    }

    @Inject(method = "halt", at = @At("HEAD"))
    private void halt(boolean waitForServer, CallbackInfo ci) {
        AcademyCraft.LOGGER.info("Halting MinecraftServer");
        if (AbilitySystemServer.scheduledFuture != null) {
            AbilitySystemServer.scheduledFuture.cancel(true);
        }
    }
}