package org.academy.api.common.arc;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

public record ArcPath(BasePath path, List<PathModifier> modifiers, float resolution, List<Branch> branches) {
    private static final StreamCodec<ByteBuf, List<PathModifier>> MOD_LIST_CODEC =
            PathModifier.CODEC.apply(ByteBufCodecs.list());
    private static final StreamCodec<ByteBuf, List<Branch>> BRANCH_LIST_CODEC =
            Branch.CODEC.apply(ByteBufCodecs.list());

    public static final StreamCodec<ByteBuf, ArcPath> CODEC = StreamCodec.composite(
            BasePath.CODEC, ArcPath::path,
            MOD_LIST_CODEC, ArcPath::modifiers,
            ByteBufCodecs.FLOAT, ArcPath::resolution,
            BRANCH_LIST_CODEC, ArcPath::branches,
            ArcPath::new
    );
}