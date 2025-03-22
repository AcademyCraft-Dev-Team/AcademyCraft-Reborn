package org.academy.api.server.network;

import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.api.common.network.packet.ClientToServerPacket;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface ClientToServerPacketHandler {
    void handle(@NotNull ServerGamePacketListenerImpl serverPacketListener, @NotNull ClientToServerPacket packet);
}