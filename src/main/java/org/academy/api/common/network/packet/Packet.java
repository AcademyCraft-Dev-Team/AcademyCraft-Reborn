package org.academy.api.common.network.packet;

import net.minecraft.network.PacketListener;
import org.academy.api.common.network.PacketType;
import org.jetbrains.annotations.Nullable;

public abstract class Packet<T extends PacketListener, P extends Packet<T, P>> {
    @Nullable
    private T packetListener;

    public T getPacketListener() {
        if (packetListener == null) {
            throw new IllegalStateException("Cannot get PacketListener on the sending side; it is only available for a received packet.");
        }
        return packetListener;
    }

    public final void setPacketListener(T packetListener) {
        this.packetListener = packetListener;
    }

    public abstract PacketType<T, P> getPacketType();
}