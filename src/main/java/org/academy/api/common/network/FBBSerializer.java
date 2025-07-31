package org.academy.api.common.network;

import net.minecraft.network.FriendlyByteBuf;

@FunctionalInterface
public interface FBBSerializer<T> {
    void serialize(FriendlyByteBuf buffer, T value);
}