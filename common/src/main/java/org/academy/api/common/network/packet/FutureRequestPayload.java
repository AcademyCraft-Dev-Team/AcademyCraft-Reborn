package org.academy.api.common.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

public abstract class FutureRequestPayload {
    public abstract void readPayload(@NotNull FriendlyByteBuf buf);

    public abstract void writePayload(@NotNull FriendlyByteBuf buf);
}