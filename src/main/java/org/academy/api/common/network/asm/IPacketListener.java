package org.academy.api.common.network.asm;

import org.academy.api.common.network.packet.IPacket;

public interface IPacketListener {
    void handlePacket(IPacket<?> packet);
    <T extends IPacket<?>> Class<T> getPacketType();
}