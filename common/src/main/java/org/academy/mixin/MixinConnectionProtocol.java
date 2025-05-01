package org.academy.mixin;

import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
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
            ConnectionProtocol.PacketSet<ClientGamePacketListener> clientGamePacketListenerPacketSet =
                    (ConnectionProtocol.PacketSet<ClientGamePacketListener>)
                            protocolBuilder.flows.get(PacketFlow.CLIENTBOUND);
            clientGamePacketListenerPacketSet.addPacket(S2CPacket.class, S2CPacket::new);
            ConnectionProtocol.PacketSet<ServerGamePacketListener> serverGamePacketListenerPacketSet =
                    (ConnectionProtocol.PacketSet<ServerGamePacketListener>)
                            protocolBuilder.flows.get(PacketFlow.SERVERBOUND);
            serverGamePacketListenerPacketSet.addPacket(C2SPacket.class, C2SPacket::new);
        }
    }
}