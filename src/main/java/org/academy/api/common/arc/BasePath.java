package org.academy.api.common.arc;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.academy.api.common.arc.data.PathData;
import org.academy.api.common.util.UncheckedUtil;
import org.joml.Matrix4f;

public interface BasePath {
    StreamCodec<ByteBuf, BasePath> CODEC = new StreamCodec<>() {
        @Override
        public BasePath decode(ByteBuf buffer) {
            var type = PathType.CODEC.decode(buffer);
            return type.codec().decode(buffer);
        }

        @Override
        public void encode(ByteBuf buffer, BasePath value) {
            var type = value.getType();
            PathType.CODEC.encode(buffer, type);
            type.codec().encode(buffer, UncheckedUtil.uncheckedCast(value));
        }
    };

    PathData generate(float resolution);

    PathType<? extends BasePath> getType();

    BasePath transform(Matrix4f transform);
}