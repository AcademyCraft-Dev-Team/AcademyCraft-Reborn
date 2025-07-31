package org.academy.api.common.network.future;

import net.minecraft.network.PacketListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class RequestPayload<P extends PacketListener, R extends ResponsePayload<?>> extends Payload<P> {
    public P packetListener;

    protected RequestPayload(@Nullable P packetListener) {
        this.packetListener = packetListener;
    }

    @NotNull
    public P getPacketListener() {
        if (packetListener == null) {
            throw new IllegalStateException("Cannot get PacketListener on the sending side; it is only available for a received packet.");
        }
        return packetListener;
    }

    @NotNull
    public abstract PayloadType<?, R> getExpectedResponsePayloadType();
}