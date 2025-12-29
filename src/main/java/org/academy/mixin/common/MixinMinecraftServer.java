package org.academy.mixin.common;

import net.minecraft.server.MinecraftServer;
import org.academy.AcademyCraftServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServer implements MinecraftServerContext {
    @Unique
    @Nullable
    private AcademyCraftServer academyCraftServer;

    @Inject(method = "halt", at = @At("HEAD"))
    private void halt(boolean waitForServer, CallbackInfo ci) {
        getAcademyCraftServer().getAbilitySystemServer().halt();
    }

    @Override
    public AcademyCraftServer getAcademyCraftServer() {
        if (academyCraftServer == null) throw new IllegalStateException("AcademyCraftServer has not been initialized.");
        return academyCraftServer;
    }

    @Override
    public void setAcademyCraftServer(AcademyCraftServer academyCraftServer) {
        this.academyCraftServer = academyCraftServer;
    }

    @Override
    public MinecraftServer getMinecraftServer() {
        return (MinecraftServer) (Object) this;
    }
}