package org.academy.api.common.network.future;

import net.minecraft.network.PacketListener;
import org.academy.api.common.registries.Registries;

import java.util.function.Function;

public final class PayloadType<L extends PacketListener, P extends Payload<L>> {
    private final Class<P> payloadClass;
    private final Function<L, P> factory;

    public PayloadType(Class<P> payloadClass, Function<L, P> factory) {
        this.payloadClass = payloadClass;
        this.factory = factory;
    }

    public Class<P> getPayloadClass() {
        return payloadClass;
    }

    public Function<L, P> getFactory() {
        return factory;
    }

    public int getPayloadId() {
        return Registries.PAYLOAD_TYPES.getId(this);
    }
}