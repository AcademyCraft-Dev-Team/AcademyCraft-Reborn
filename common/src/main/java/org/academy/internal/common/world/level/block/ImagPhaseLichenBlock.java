package org.academy.internal.common.world.level.block;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.GlowLichenBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.PushReaction;
import org.academy.AcademyCraft;

public class ImagPhaseLichenBlock extends GlowLichenBlock {
    public static final ResourceLocation ID = new ResourceLocation(AcademyCraft.MOD_ID, "block/imag_phase_lichen");

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