package org.academy.api.common.network.asm;

import org.academy.api.common.network.packet.Packet;

public interface IPacketListener {
    void handlePacket(Packet<?> packet);
    <T extends Packet<?>> Class<T> getPacketType();
}