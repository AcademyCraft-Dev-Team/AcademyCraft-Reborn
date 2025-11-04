/*
package org.academy.internal.common.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.util.ParticleUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import org.academy.internal.common.core.particles.ParticleTypes;
import org.academy.internal.common.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

public final class ImagiphasePlasma extends LiquidBlock {
    public ImagiphasePlasma(BlockBehaviour.Properties properties) {
        super(Fluids.IMAGIPHASE_PLASMA.get(),
                properties
                        .replaceable()
                        .noCollision()
                        .randomTicks()
                        .strength(100.0F)
                        .pushReaction(PushReaction.DESTROY)
                        .noLootTable()
                        .liquid()
                        .sound(SoundType.EMPTY));
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        ParticleUtils.spawnParticleInBlock(level, pos, 1, ParticleTypes.IMAG_PHASE_FLUID.get());
    }

    @Override
    public ItemStack pickupBlock(@Nullable LivingEntity livingEntity, LevelAccessor levelAccessor, BlockPos pos, BlockState blockState) {
        return ItemStack.EMPTY;
    }
}*/
