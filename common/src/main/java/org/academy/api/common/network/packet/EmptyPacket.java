package org.academy.api.common.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketListener;
import org.jetbrains.annotations.NotNull;

public abstract class EmptyPacket<T extends PacketListener> extends IPacket<T> {
    @Override
    public void write(@NotNull FriendlyByteBuf buf) {
    }

    @Override
    public void read(@NotNull FriendlyByteBuf buf) {
    }
}