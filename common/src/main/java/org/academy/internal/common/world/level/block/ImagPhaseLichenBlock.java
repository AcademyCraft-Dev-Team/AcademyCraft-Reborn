package org.academy.internal.common.world.level.block;

import net.minecraft.world.level.block.GlowLichenBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.PushReaction;

public class ImagPhaseLichenBlock extends GlowLichenBlock {
    public ImagPhaseLichenBlock() {
        super(Properties.of()
                .replaceable()
                .noCollission()
                .strength(0.2F)
                .sound(SoundType.VINE)
                .lightLevel(GlowLichenBlock.emission(7))
                .ignitedByLava()
                .pushReaction(PushReaction.DESTROY));
    }
}