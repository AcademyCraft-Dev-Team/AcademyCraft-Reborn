package org.academy.internal.common.world.level.block;

import net.minecraft.world.level.block.GlowLichenBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.PushReaction;

public final class ImagiphaseLichenBlock extends GlowLichenBlock {
    public ImagiphaseLichenBlock(Properties  properties) {
        super(properties
                .replaceable()
                .noCollision()
                .strength(0.2F)
                .sound(SoundType.VINE)
                .lightLevel(GlowLichenBlock.emission(7))
                .ignitedByLava()
                .pushReaction(PushReaction.DESTROY));
    }
}