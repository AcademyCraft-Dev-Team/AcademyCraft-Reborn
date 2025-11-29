package org.academy.api.common.arc;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.academy.api.common.registries.Registries;

public record PathType<T extends BasePath>(StreamCodec<ByteBuf, T> codec) {
    public static final StreamCodec<ByteBuf, PathType<?>> CODEC =
            ByteBufCodecs.idMapper(Registries.PATH_TYPES);
}