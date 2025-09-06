package org.academy.api.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketListener;
import net.minecraft.network.codec.StreamCodec;
import org.academy.api.common.network.packet.Packet;
import org.academy.api.common.registries.Registries;

public final class PacketType<L extends PacketListener, P extends Packet<L, P>> {
    private final Class<P> packetClass;
    private final StreamCodec<ByteBuf, P> codec;

    public PacketType(Class<P> packetClass, StreamCodec<ByteBuf, P> codec) {
        this.packetClass = packetClass;
        this.codec = codec;
    }

    public Class<P> getPacketClass() {
        return packetClass;
    }

    public StreamCodec<ByteBuf, P> getCodec() {
        return codec;
    }

    public int getPacketId() {
        return Registries.PACKET_TYPES.getId(this);
    }
}