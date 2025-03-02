package org.academy.api.client.network;

import net.minecraft.client.multiplayer.ClientPacketListener;

public interface AcademyCraftRequestHandlerClient {
    void handle(ClientPacketListener listener);
}