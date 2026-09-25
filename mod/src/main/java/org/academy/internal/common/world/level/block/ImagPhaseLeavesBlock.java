package org.academy.internal.common.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FallingParticlesLeavesBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.sounds.AmbientLeavesBlockSoundPlayer;
import net.minecraft.world.level.material.PushReaction;
import org.academy.internal.common.core.particles.ParticleTypes;

public final class ImagPhaseLeavesBlock extends FallingParticlesLeavesBlock {

    public ImagPhaseLeavesBlock(Properties properties) {
        super(0.5F, AmbientLeavesBlockSoundPlayer.noAmbientSound(), properties
                .strength(0.2F)
                .randomTicks()
                .noOcclusion()
                .sound(SoundType.CHAIN)
                .isValidSpawn((state, level, pos, entityType)
                        -> entityType == EntityTypes.OCELOT || entityType == EntityTypes.PARROT)
                .isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos, nearPlaneBox) -> false)
                .ignitedByLava()
                .pushReaction(PushReaction.POPPED)
                .isRedstoneConductor((state, level, pos) -> false));
    }


    @Override
    protected void spawnFallingLeavesParticle(Level level, BlockPos pos, RandomSource random) {
        level.addParticle(
                ParticleTypes.IMAG_PHASE_FLUID.get(),
                pos.getX() + random.nextDouble(),
                pos.getY() - 0.05,
                pos.getZ() + random.nextDouble(),
                0.0,
                -0.015 - random.nextDouble() * 0.025,
                0.0
        );
    }
}
