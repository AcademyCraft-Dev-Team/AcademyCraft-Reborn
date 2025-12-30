package org.academy.mixin.common;

import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public abstract class MixinPlayerList {
    @Shadow
    public abstract MinecraftServer getServer();

    @Inject(method = "placeNewPlayer",at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;teleport(DDDFF)V"))
    private void onPlayerConnect(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci) {
        var context = (MinecraftServerContext) getServer();
        context.getAcademyCraftServer().getAbilitySystemServer().onPlayerLogin(player);
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void onPlayerDisconnect(ServerPlayer player, CallbackInfo ci) {
        var context = (MinecraftServerContext) getServer();
        context.getAcademyCraftServer().getAbilitySystemServer().onPlayerLogout(player);
    }
}