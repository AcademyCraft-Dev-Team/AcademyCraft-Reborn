package org.academy.api.common.arc.property;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.joml.Vector3fc;

public record ColorKnot(float progress, Vector3fc color) {
    public static final StreamCodec<ByteBuf, ColorKnot> CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, ColorKnot::progress,
            ByteBufCodecs.VECTOR3F, ColorKnot::color,
            ColorKnot::new
    );
}