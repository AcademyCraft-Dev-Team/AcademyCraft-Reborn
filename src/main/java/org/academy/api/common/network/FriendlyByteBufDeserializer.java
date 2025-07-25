package org.academy.api.common.network;

import net.minecraft.network.FriendlyByteBuf;

@FunctionalInterface
public interface FriendlyByteBufDeserializer<T> {
    T deserialize(FriendlyByteBuf buffer);
}