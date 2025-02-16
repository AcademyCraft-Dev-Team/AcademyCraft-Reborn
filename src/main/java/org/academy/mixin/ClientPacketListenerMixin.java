package org.academy.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import org.academy.api.client.network.AcademyCraftClientPacketHandlers;
import org.academy.api.client.network.NetworkSystemClient;
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
    @Unique
    ClientPacketListener instance;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo info) {
        instance = (ClientPacketListener) (Object) this;
        NetworkSystemClient.connection = this.connection;
    }

    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    public void handleCustomPayload(ClientboundCustomPayloadPacket packet, CallbackInfo ci) {
        ResourceLocation identifier = packet.getIdentifier();
        if (AcademyCraftClientPacketHandlers.HANDLER_MAP.containsKey(identifier)) {
            AcademyCraftClientPacketHandlers.HANDLER_MAP.get(identifier).handle(instance, packet);
            ci.cancel();
        }
    }
}