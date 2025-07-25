package org.academy.api.common.network.future;

import net.minecraft.network.PacketListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings({"rawtypes"})
public abstract class IRequestPayload<P extends PacketListener, R extends IResponsePayload> extends IPayload<P> {
    public P packetListener;

    protected IRequestPayload(@Nullable P packetListener) {
        this.packetListener = packetListener;
    }

    @NotNull
    public P getPacketListener() {
        if (packetListener == null) {
            throw new IllegalStateException("Cannot get PacketListener on the sending side; it is only available for a received packet.");
        }
        return packetListener;
    }

    public abstract Class<R> getExpectedResponseType();
}