package org.academy.api.server.network;

import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

@FunctionalInterface
public interface PacketHandlerServer {
    void handle(ServerGamePacketListenerImpl listener, ServerboundCustomPayloadPacket packet);
}