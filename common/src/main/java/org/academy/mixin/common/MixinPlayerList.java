package org.academy.mixin.common;

import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.academy.AcademyCraft;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.server.world.level.storage.WorldData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public abstract class MixinPlayerList {
    @Inject(method = "placeNewPlayer",at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;teleport(DDDFF)V"))
    private void onPlayerConnect(Connection netManager, ServerPlayer player, CallbackInfo ci) {
        AcademyCraft.LOGGER.debug("Init player.");
        AbilitySystemServer.ServerLifecycleHooks.initPlayer(player);
        WorldData.saveData();
    }
}