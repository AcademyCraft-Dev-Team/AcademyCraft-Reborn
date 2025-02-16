package org.academy.api.client.network;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;

@FunctionalInterface
public interface ClientHandler {
    void handle(ClientPacketListener listener, ClientboundCustomPayloadPacket packet);
}