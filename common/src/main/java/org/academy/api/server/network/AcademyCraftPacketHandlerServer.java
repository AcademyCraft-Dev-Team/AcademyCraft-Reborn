package org.academy.api.server.network;

import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

@FunctionalInterface
public interface AcademyCraftPacketHandlerServer {
    void handle(ServerGamePacketListenerImpl listener, ServerboundCustomPayloadPacket packet);
}