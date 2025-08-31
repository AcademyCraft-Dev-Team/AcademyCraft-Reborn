package org.academy.api.common.network;

import net.minecraft.network.PacketListener;
import org.academy.api.common.network.packet.Packet;
import org.academy.api.common.registries.Registries;

import java.util.function.Function;

public final class PacketType<L extends PacketListener, P extends Packet<L>> {
    private final Class<P> packetClass;
    private final Function<? extends PacketListener, P> factory;

    public PacketType(Class<P> packetClass, Function<L, P> factory) {
        this.packetClass = packetClass;
        this.factory = factory;
    }

    public Class<P> getPacketClass() {
        return packetClass;
    }

    @SuppressWarnings("unchecked")
    public <T extends PacketListener> Function<T, P> getFactory() {
        return (Function<T, P>) factory;
    }

    public int getPacketId() {
        return Registries.PACKET_TYPES.getId(this);
    }
}