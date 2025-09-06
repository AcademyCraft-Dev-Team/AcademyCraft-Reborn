package org.academy.api.common.network.asm;

import net.minecraft.network.PacketListener;
import org.academy.api.common.network.packet.Packet;

public interface IPacketListener<L extends PacketListener, P extends Packet<L, P>> {
    void handlePacket(Packet<?, ?> packet);

    Class<P> getPacketClass();
}