package org.academy.api.client.network;

import net.minecraft.client.multiplayer.ClientPacketListener;
import org.academy.api.common.network.packet.S2CPacket;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface S2CPacketHandler {
    void handle(@NotNull ClientPacketListener listener, @NotNull S2CPacket packet);
}