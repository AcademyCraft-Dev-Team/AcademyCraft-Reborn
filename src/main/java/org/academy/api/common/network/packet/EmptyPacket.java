package org.academy.api.common.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class EmptyPacket<T extends PacketListener> extends Packet<T> {
    protected EmptyPacket(@Nullable T listener) {
        super(listener);
    }

    @Override
    public final void write(@NotNull FriendlyByteBuf buf) {
    }

    @Override
    public final void read(@NotNull FriendlyByteBuf buf) {
    }
}