package org.academy.api.client.network;

import net.minecraft.client.multiplayer.ClientPacketListener;
import org.academy.api.common.network.packet.ServerToClientPacket;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface ServerToClientPacketHandler {
    void handle(@NotNull ClientPacketListener listener, @NotNull ServerToClientPacket packet);
}