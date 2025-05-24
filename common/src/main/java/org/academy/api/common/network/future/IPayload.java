package org.academy.api.common.network.future;

import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

public interface IPayload {
    void write(@NotNull FriendlyByteBuf buf);

    void read(@NotNull FriendlyByteBuf buf);
}