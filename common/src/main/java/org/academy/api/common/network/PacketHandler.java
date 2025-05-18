package org.academy.api.common.network;

import org.academy.api.common.network.packet.IPacket;

public interface PacketHandler {
    void handlePacket(IPacket<?> packet);
    <T extends IPacket<?>> Class<T> getPacketType();
}