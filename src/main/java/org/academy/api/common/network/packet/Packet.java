package org.academy.api.common.network.packet;

import net.minecraft.network.PacketListener;
import org.academy.api.common.network.PacketType;
import org.jetbrains.annotations.NotNull;

public abstract class Packet<T extends PacketListener, P extends Packet<T, P>> {
    private T packetListener;

    @NotNull
    public T getPacketListener() {
        if (packetListener == null) {
            throw new IllegalStateException("Cannot get PacketListener on the sending side; it is only available for a received packet.");
        }
        return packetListener;
    }

    public final void setPacketListener(@NotNull T packetListener) {
        this.packetListener = packetListener;
    }

    public abstract @NotNull PacketType<T, P> getPacketType();
}