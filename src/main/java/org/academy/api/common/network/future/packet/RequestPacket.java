package org.academy.api.common.network.future.packet;

import net.minecraft.network.PacketListener;
import org.academy.api.common.network.packet.PacketType;
import org.academy.api.common.network.packet.Packet;

public abstract class RequestPacket<
        REQ_L extends PacketListener,
        REQ_P extends Packet<REQ_L, REQ_P>,
        RES_L extends PacketListener,
        RES_P extends ResponsePacket<RES_L, RES_P>
        > extends Packet<REQ_L, REQ_P> {
    public abstract PacketType<RES_L, RES_P> getResponsePacketType();
}