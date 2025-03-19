package org.academy.api.common.network;

import net.minecraft.network.FriendlyByteBuf;

@FunctionalInterface
public interface FriendlyByteBufSerializer<T> {
    FriendlyByteBuf serialize(FriendlyByteBuf buffer, T value);
}