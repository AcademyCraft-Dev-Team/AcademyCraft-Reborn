package org.academy.api.server.network;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.api.common.network.packet.ClientToServerPacket;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface ClientToServerPacketHandler {
    void handle(@NotNull ServerGamePacketListenerImpl listener, @NotNull ClientToServerPacket packet);
}