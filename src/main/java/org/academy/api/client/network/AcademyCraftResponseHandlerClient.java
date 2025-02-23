package org.academy.api.client.network;

import net.minecraft.client.multiplayer.ClientPacketListener;

public interface AcademyCraftResponseHandlerClient {
    void handle(ClientPacketListener listener);
}