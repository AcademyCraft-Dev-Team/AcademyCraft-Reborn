package org.academy.mixin.common;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.ProtocolInfoBuilder;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.common.network.packet.S2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.function.Consumer;

@SuppressWarnings("unchecked")
@Mixin(ProtocolInfoBuilder.class)
public abstract class MixinProtocolInfoBuilder {
    @SuppressWarnings("rawtypes")
    @Inject(method = "protocol", locals = LocalCapture.CAPTURE_FAILSOFT, at = @At(value = "INVOKE", target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V"))
    private static <L extends PacketListener, B extends ByteBuf> void protocol(
            ConnectionProtocol protocol, PacketFlow flow, Consumer<ProtocolInfoBuilder<L, B>> setup, CallbackInfoReturnable<ProtocolInfo.Unbound<L, B>> cir, ProtocolInfoBuilder protocolinfobuilder
    ) {
        switch (flow) {
            case CLIENTBOUND -> protocolinfobuilder.addPacket(S2CPacket.TYPE, S2CPacket.STREAM_CODEC);
            case SERVERBOUND -> protocolinfobuilder.addPacket(C2SPacket.TYPE, C2SPacket.STREAM_CODEC);
        }
    }
}