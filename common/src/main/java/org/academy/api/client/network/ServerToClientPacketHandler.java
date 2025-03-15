package org.academy.api.client.network;

import net.minecraft.network.protocol.game.ClientGamePacketListener;
import org.academy.api.common.network.packet.ServerToClientPacket;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface ServerToClientPacketHandler {
    void handle(@NotNull ClientGamePacketListener handler, @NotNull ServerToClientPacket packet);
}