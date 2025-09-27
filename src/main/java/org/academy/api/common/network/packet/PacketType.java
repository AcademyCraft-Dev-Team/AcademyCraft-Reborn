package org.academy.api.common.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketListener;
import net.minecraft.network.codec.StreamCodec;
import org.academy.api.common.registries.Registries;

public record PacketType<L extends PacketListener, P extends Packet<L, P>>
        (Class<P> packetClass, StreamCodec<ByteBuf, P> codec) {
    public int getPacketId() {
        return Registries.PACKET_TYPES.getId(this);
    }
}