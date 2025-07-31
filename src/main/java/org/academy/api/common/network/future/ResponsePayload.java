package org.academy.api.common.network.future;

import net.minecraft.network.PacketListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ResponsePayload<P extends PacketListener> extends Payload<P> {
    protected ResponsePayload(@NotNull P packetListener) {
        super(packetListener);
    }

    protected ResponsePayload() {
    }
}