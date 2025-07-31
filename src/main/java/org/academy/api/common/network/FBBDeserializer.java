package org.academy.api.common.network;

import net.minecraft.network.FriendlyByteBuf;

@FunctionalInterface
public interface FBBDeserializer<T> {
    T deserialize(FriendlyByteBuf buffer);
}