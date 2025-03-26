package org.academy.mixin;

import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.AbilitySystemServer;
import org.academy.api.server.network.AcademyCraftPacketHandlersServer;
import org.academy.internal.server.world.level.storage.AcademyCraftWorldData;
import org.spongepowered.asm.mixin.Final;
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
    @Shadow @Final private MinecraftServer server;
    @Unique
    ServerGamePacketListenerImpl academyCraft$instance;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo info) {
        academyCraft$instance = (ServerGamePacketListenerImpl) (Object) this;
        AbilitySystemServer.MinecraftServerThread.initPlayer(player);
        AcademyCraftWorldData.saveData();
    }

    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    public void handleCustomPayload(ServerboundCustomPayloadPacket packet, CallbackInfo ci) {
        PacketUtils.ensureRunningOnSameThread(packet, academyCraft$instance, this.server);
        ResourceLocation identifier = packet.getIdentifier();
        if (AcademyCraftPacketHandlersServer.HANDLER_MAP.containsKey(identifier)) {
            AcademyCraftPacketHandlersServer.HANDLER_MAP.get(identifier).handle(academyCraft$instance, packet);
            ci.cancel();
        }
    }
}