package org.academy.api.client.network;

import net.minecraft.client.multiplayer.ClientPacketListener;

public interface ClientResponseHandler {
    void handle(ClientPacketListener listener);
}