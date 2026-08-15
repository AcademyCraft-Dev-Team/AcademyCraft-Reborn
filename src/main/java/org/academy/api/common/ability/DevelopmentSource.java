package org.academy.api.common.ability;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.InteractionHand;
import org.jspecify.annotations.Nullable;

/** Identifies the energy source backing an ability-development session. */
public record DevelopmentSource(@Nullable BlockPos blockPos, @Nullable InteractionHand hand) {
    public static final StreamCodec<ByteBuf, DevelopmentSource> CODEC = StreamCodec.of(
            (buffer, source) -> {
                ByteBufCodecs.BOOL.encode(buffer, source.portable());
                if (source.portable()) {
                    ByteBufCodecs.VAR_INT.encode(buffer, source.hand.ordinal());
                } else {
                    BlockPos.STREAM_CODEC.encode(buffer, source.blockPos);
                }
            },
            buffer -> {
                if (ByteBufCodecs.BOOL.decode(buffer)) {
                    var ordinal = ByteBufCodecs.VAR_INT.decode(buffer);
                    var hands = InteractionHand.values();
                    return tablet(ordinal >= 0 && ordinal < hands.length
                            ? hands[ordinal]
                            : InteractionHand.MAIN_HAND);
                }
                return block(BlockPos.STREAM_CODEC.decode(buffer));
            }
    );

    public DevelopmentSource {
        if ((blockPos == null) == (hand == null)) {
            throw new IllegalArgumentException("Development source needs exactly one location");
        }
        if (blockPos != null) blockPos = blockPos.immutable();
    }

    public static DevelopmentSource block(BlockPos pos) {
        if (pos == null) throw new IllegalArgumentException("Developer position cannot be null");
        return new DevelopmentSource(pos, null);
    }

    public static DevelopmentSource tablet(InteractionHand hand) {
        return new DevelopmentSource(null, hand == null ? InteractionHand.MAIN_HAND : hand);
    }

    public boolean portable() {
        return hand != null;
    }

    public String cacheKey() {
        return portable() ? "tablet:" + hand.name() : "block:" + blockPos.asLong();
    }
}
