package org.academy.api.common.network;

import net.minecraft.network.FriendlyByteBuf;

@FunctionalInterface
public interface FriendlyByteBufSerializer<T> {
    /**
     * 将数据序列化
     *
     * @param buffer 用于存储的 FriendlyByteBuf
     * @param value 需要序列化的数据
     */
    void serialize(FriendlyByteBuf buffer, T value);
}