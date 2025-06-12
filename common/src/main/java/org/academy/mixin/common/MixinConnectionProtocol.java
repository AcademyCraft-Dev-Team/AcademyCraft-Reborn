package org.academy.mixin.common;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.common.network.packet.S2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SuppressWarnings("unchecked")
@Mixin(ConnectionProtocol.class)
public abstract class MixinConnectionProtocol {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void init(String string, int i, int id, ConnectionProtocol.ProtocolBuilder protocolBuilder, CallbackInfo ci) {
        if (string.equals("PLAY")) {
            ConnectionProtocol.PacketSet<ClientPacketListener> clientGamePacketListenerPacketSet =
                    (ConnectionProtocol.PacketSet<ClientPacketListener>)
                            protocolBuilder.flows.get(PacketFlow.CLIENTBOUND);
            clientGamePacketListenerPacketSet.addPacket(S2CPacket.class, S2CPacket::new);
            ConnectionProtocol.PacketSet<ServerGamePacketListenerImpl> serverGamePacketListenerPacketSet =
                    (ConnectionProtocol.PacketSet<ServerGamePacketListenerImpl>)
                            protocolBuilder.flows.get(PacketFlow.SERVERBOUND);
            serverGamePacketListenerPacketSet.addPacket(C2SPacket.class, C2SPacket::new);
        }
    }
}