package org.academy.api.common.ability;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.InteractionHand;

import java.util.Objects;

public sealed interface DevelopmentSource
        permits DevelopmentSource.BlockDevelopmentSource, DevelopmentSource.TabletDevelopmentSource {
    String cacheKey();

    StreamCodec<ByteBuf, DevelopmentSource> CODEC = StreamCodec.of(
            (buffer, source) -> {
                if (source instanceof TabletDevelopmentSource(InteractionHand hand)) {
                    ByteBufCodecs.BOOL.encode(buffer, true);
                    ByteBufCodecs.VAR_INT.encode(buffer, hand.ordinal());
                } else if (source instanceof BlockDevelopmentSource(BlockPos blockPos)) {
                    ByteBufCodecs.BOOL.encode(buffer, false);
                    BlockPos.STREAM_CODEC.encode(buffer, blockPos);
                }
            },
            buffer -> {
                if (ByteBufCodecs.BOOL.decode(buffer)) {
                    var ordinal = ByteBufCodecs.VAR_INT.decode(buffer);
                    var hands = InteractionHand.values();
                    return new TabletDevelopmentSource(
                            ordinal >= 0 && ordinal < hands.length
                                    ? hands[ordinal]
                                    : InteractionHand.MAIN_HAND);
                }
                return new BlockDevelopmentSource(BlockPos.STREAM_CODEC.decode(buffer));
            }
    );

    static DevelopmentSource block(BlockPos pos) {
        return new BlockDevelopmentSource(Objects.requireNonNull(pos, "pos"));
    }

    static DevelopmentSource tablet(InteractionHand hand) {
        return new TabletDevelopmentSource(hand);
    }

    record BlockDevelopmentSource(BlockPos blockPos) implements DevelopmentSource {
        public String cacheKey() {
            return "block:" + blockPos.asLong();
        }
    }

    record TabletDevelopmentSource(InteractionHand hand) implements DevelopmentSource {
        public String cacheKey() {
            return "tablet:" + hand.name();
        }
    }
}
