package org.academy.api.common.arc;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.academy.api.common.registries.Registries;

public record PathModifierType<T extends PathModifier>(StreamCodec<ByteBuf, T> codec) {
    public static final StreamCodec<ByteBuf, PathModifierType<?>> CODEC =
            ByteBufCodecs.idMapper(Registries.PATH_MODIFIER_TYPES);
}