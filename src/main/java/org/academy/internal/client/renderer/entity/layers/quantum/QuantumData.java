package org.academy.internal.client.renderer.entity.layers.quantum;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record QuantumData(boolean active, float intensity, int color, int duration) {
    //默认值：不激活，强度0，颜色青色 (ARGB)
    private static final QuantumData DEFAULT = new QuantumData(false, 0.0f, 0xFF33CCFF, 0);

    public static final StreamCodec<ByteBuf, QuantumData> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, QuantumData::active,
            ByteBufCodecs.FLOAT, QuantumData::intensity,
            ByteBufCodecs.INT, QuantumData::color,
            ByteBufCodecs.INT, QuantumData::duration,
            QuantumData::new
    );

    public static QuantumData getDefault() {
        return DEFAULT;
    }
}