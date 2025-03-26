package org.academy.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import org.academy.api.client.network.AcademyCraftNetworkSystemClient;
import org.academy.api.client.network.AcademyCraftPacketHandlersClient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    @Shadow
    @Final
    private Connection connection;
    @Shadow @Final private Minecraft minecraft;
    @Unique
    ClientPacketListener academyCraft$instance;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo info) {
        academyCraft$instance = (ClientPacketListener) (Object) this;
        AcademyCraftNetworkSystemClient.connection = this.connection;
    }

    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    public void handleCustomPayload(ClientboundCustomPayloadPacket packet, CallbackInfo ci) {
        PacketUtils.ensureRunningOnSameThread(packet, academyCraft$instance, this.minecraft);
        ResourceLocation identifier = packet.getIdentifier();
        if (AcademyCraftPacketHandlersClient.HANDLER_MAP.containsKey(identifier)) {
            AcademyCraftPacketHandlersClient.HANDLER_MAP.get(identifier).handle(academyCraft$instance, packet);
            ci.cancel();
        }
    }
}