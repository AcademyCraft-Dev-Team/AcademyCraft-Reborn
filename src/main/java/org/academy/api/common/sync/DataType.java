package org.academy.api.common.sync;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.academy.api.common.registries.Registries;

/**
 * 最小同步单位喵
 */
public record DataType<V>(StreamCodec<ByteBuf, V> codec) {
    public static final StreamCodec<ByteBuf, DataType<?>> CODEC =
            ByteBufCodecs.idMapper(Registries.DATA_TYPES);
}