package org.academy.internal.common.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.GlowLichenBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.PushReaction;

public final class ImagPhaseLichenBlock extends GlowLichenBlock {
    public static final MapCodec<GlowLichenBlock> CODEC = simpleCodec(ImagPhaseLichenBlock::new);

    public ImagPhaseLichenBlock(Properties properties) {
        super(properties
                .replaceable()
                .noCollision()
                .strength(0.2F)
                .sound(SoundType.VINE)
                .lightLevel(GlowLichenBlock.emission(7))
                .ignitedByLava()
                .pushReaction(PushReaction.DESTROY));
    }

    @Override
    public MapCodec<GlowLichenBlock> codec() {
        return CODEC;
    }
}
