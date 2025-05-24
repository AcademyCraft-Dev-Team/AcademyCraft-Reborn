package org.academy.api.common.network.future;

import net.minecraft.network.PacketListener;

import java.util.function.Supplier;

public abstract class IRequestPayload<T extends PacketListener, P extends IResponsePayload> implements IPayload {
    public Supplier<T> packetListenerSupplier;

    public abstract Class<P> getExpectedResponseType();
}