package org.academy.api.server.network;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.api.common.network.packet.C2SPacket;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface C2SPacketHandler {
    void handle(@NotNull ServerGamePacketListenerImpl listener, @NotNull C2SPacket packet);
}