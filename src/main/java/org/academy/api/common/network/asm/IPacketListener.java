package org.academy.api.common.network.asm;

import net.minecraft.network.PacketListener;
import org.academy.api.common.network.packet.Packet;

public interface IPacketListener {
    void handlePacket(Packet<?, ?> packet);

    <L extends PacketListener, P extends Packet<L, P>> Class<P> getPacketClass();
}