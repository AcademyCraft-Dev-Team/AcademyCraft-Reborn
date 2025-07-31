package org.academy.api.common.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketListener;
import org.academy.api.common.network.PacketType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class Packet<T extends PacketListener> {
    private final T packetListener;

    protected Packet(@Nullable T packetListener) {
        this.packetListener = packetListener;
    }

    @NotNull
    public T getPacketListener() {
        if (packetListener == null) {
            throw new IllegalStateException("Cannot get PacketListener on the sending side; it is only available for a received packet.");
        }
        return packetListener;
    }

    public abstract void read(@NotNull FriendlyByteBuf buf);

    public abstract void write(@NotNull FriendlyByteBuf buf);

    @NotNull
    public abstract PacketType<T, ? extends Packet<T>> getPacketType();
}