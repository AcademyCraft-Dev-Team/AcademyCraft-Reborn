package org.academy.api.server.network;

import net.minecraft.server.network.ServerGamePacketListenerImpl;

public interface AcademyCraftRequestHandlerServer {
    void handle(ServerGamePacketListenerImpl serverGamePacketListenerImpl);
}