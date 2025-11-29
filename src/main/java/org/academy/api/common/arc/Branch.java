package org.academy.api.common.arc;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record Branch(float attachmentProgress, ArcPath child) {
    public static final StreamCodec<ByteBuf, Branch> CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, Branch::attachmentProgress,
            StreamCodec.recursive(_ -> ArcPath.CODEC), Branch::child,
            Branch::new
    );
}