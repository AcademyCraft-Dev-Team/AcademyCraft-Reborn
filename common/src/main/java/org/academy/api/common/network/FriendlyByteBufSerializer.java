package org.academy.api.common.network;

import net.minecraft.network.FriendlyByteBuf;

@FunctionalInterface
public interface FriendlyByteBufSerializer<T> {
    void serialize(FriendlyByteBuf buffer, T value);

    @SuppressWarnings("unchecked")
    default Class<T> getType() {
        return (Class<T>) getClass().getGenericInterfaces()[0].getClass();
    }
}