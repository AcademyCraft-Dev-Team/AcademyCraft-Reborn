package org.academy.api.server.network;

import net.minecraft.network.protocol.game.ServerGamePacketListener;
import org.academy.api.common.network.packet.ClientToServerPacket;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface ClientToServerPacketHandler {
    void handle(@NotNull ServerGamePacketListener handler, @NotNull ClientToServerPacket packet);
}