package org.academy.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.server.world.level.storage.AcademyCraftWorldData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplMixin {
    @Shadow
    public ServerPlayer player;
    @Unique
    ServerGamePacketListenerImpl academyCraft$instance;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo info) {
        academyCraft$instance = (ServerGamePacketListenerImpl) (Object) this;
        AbilitySystemServer.MinecraftServerThread.initPlayer(player);
        AcademyCraftWorldData.saveData();
    }
}