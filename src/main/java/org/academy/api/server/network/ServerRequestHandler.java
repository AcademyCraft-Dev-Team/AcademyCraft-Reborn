package org.academy.api.server.network;

import net.minecraft.server.network.ServerGamePacketListenerImpl;

public interface ServerRequestHandler {
    void handle(ServerGamePacketListenerImpl serverGamePacketListenerImpl);
}