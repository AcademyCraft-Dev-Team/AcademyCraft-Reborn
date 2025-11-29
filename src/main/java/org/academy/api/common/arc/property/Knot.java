package org.academy.api.common.arc.property;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record Knot(float progress, float value) {
    public static final StreamCodec<ByteBuf, Knot> CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, Knot::progress,
            ByteBufCodecs.FLOAT, Knot::value,
            Knot::new
    );
}