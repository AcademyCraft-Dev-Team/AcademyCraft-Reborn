package org.academy.mixin.common;

import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.ProtocolInfoBuilder;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.common.network.packet.S2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SuppressWarnings("rawtypes")
@Mixin(ProtocolInfoBuilder.class)
public abstract class MixinProtocolInfoBuilder {
    @Shadow
    public abstract ProtocolInfoBuilder addPacket(PacketType type, StreamCodec serializer);

    @Inject(method = "<init>", at = @At("TAIL"))
    private void init(ConnectionProtocol protocol, PacketFlow flow, CallbackInfo ci) {
        switch (flow) {
            case CLIENTBOUND -> addPacket(S2CPacket.TYPE, S2CPacket.STREAM_CODEC);
            case SERVERBOUND -> addPacket(C2SPacket.TYPE, C2SPacket.STREAM_CODEC);
        }
    }
}