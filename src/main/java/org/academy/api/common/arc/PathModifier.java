package org.academy.api.common.arc;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.academy.api.common.arc.data.PathData;
import org.academy.api.common.util.UncheckedUtil;

public interface PathModifier {
    StreamCodec<ByteBuf, PathModifier> CODEC = new StreamCodec<>() {
        @Override
        public PathModifier decode(ByteBuf buffer) {
            var type = PathModifierType.CODEC.decode(buffer);
            return type.codec().decode(buffer);
        }

        @Override
        public void encode(ByteBuf buffer, PathModifier value) {
            var type = value.getType();
            PathModifierType.CODEC.encode(buffer, type);
            type.codec().encode(buffer, UncheckedUtil.uncheckedCast(value));
        }
    };

    PathData apply(PathData data, float time);

    PathModifierType<? extends PathModifier> getType();
}