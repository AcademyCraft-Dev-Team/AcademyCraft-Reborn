package org.academy.api.common.network.future;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketListener;
import org.jetbrains.annotations.NotNull;

public abstract class Payload<T extends PacketListener> {
    private final T packetListener;

    protected Payload(@NotNull T packetListener) {
        this.packetListener = packetListener;
    }

    protected Payload() {
        this.packetListener = null;
    }

    public abstract void write(@NotNull FriendlyByteBuf buf);

    public abstract void read(@NotNull FriendlyByteBuf buf);

    @NotNull
    public T getPacketListener() {
        if (packetListener == null) {
            throw new IllegalStateException("Cannot get PacketListener on the sending side; it is only available for a received packet.");
        }
        return packetListener;
    }

    @NotNull
    public abstract PayloadType<T, ? extends Payload<T>> getPayloadType();
}