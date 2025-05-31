package org.academy.internal.common.world.level.material;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.common.core.particles.ParticleTypes;
import org.academy.internal.common.world.item.Items;
import org.academy.internal.common.world.level.block.Blocks;
import org.jetbrains.annotations.NotNull;

public abstract class ImagPhaseFluid extends FlowingFluid {
    @Override
    protected void animateTick(@NotNull Level level, @NotNull BlockPos pos, @NotNull FluidState state, @NotNull RandomSource random) {
        int particleCount = MathUtil.RANDOM.nextInt(2, 5);
        for (int i = 0; i < particleCount; i++) {
            level.addParticle(ParticleTypes.IMAG_PHASE,
                    pos.getX() + random.nextDouble(),
                    pos.getY() + random.nextDouble(),
                    pos.getZ() + random.nextDouble(),
                    0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    public @NotNull Fluid getFlowing() {
        return Fluids.FLOWING_IMAG_PHASE;
    }

    @Override
    public @NotNull Fluid getSource() {
        return Fluids.IMAG_PHASE;
    }

    @Override
    public boolean isSame(@NotNull Fluid fluid) {
        return Fluids.IMAG_PHASE == fluid || Fluids.FLOWING_IMAG_PHASE == fluid;
    }

    @Override
    protected boolean canConvertToSource(@NotNull Level level) {
        return false;
    }

    @Override
    protected void beforeDestroyingBlock(@NotNull LevelAccessor levelAccessor, @NotNull BlockPos blockPos, @NotNull BlockState blockState) {
    }

    @Override
    protected int getSlopeFindDistance(@NotNull LevelReader levelReader) {
        return 4;
    }

    @Override
    protected int getDropOff(@NotNull LevelReader levelReader) {
        return 1;
    }

    @Override
    public @NotNull Item getBucket() {
        return Items.IMAG_PHASE_BUCKET;
    }

    @Override
    protected boolean canBeReplacedWith(@NotNull FluidState pOurState, @NotNull BlockGetter pLevel, @NotNull BlockPos pPos, @NotNull Fluid pIncomingFluid, @NotNull Direction pDirection) {
        return false;
    }

    @Override
    public int getTickDelay(@NotNull LevelReader levelReader) {
        return 3;
    }

    @Override
    protected float getExplosionResistance() {
        return 50;
    }

    @Override
    protected @NotNull BlockState createLegacyBlock(@NotNull FluidState fluidState) {
        return Blocks.IMAG_PHASE.defaultBlockState().setValue(LiquidBlock.LEVEL, getLegacyLevel(fluidState));
    }

    public static class Flowing extends ImagPhaseFluid {
        @Override
        protected void createFluidStateDefinition(StateDefinition.@NotNull Builder<Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
            builder.add(LEVEL);
        }

        @Override
        public boolean isSource(@NotNull FluidState fluidState) {
            return false;
        }

        @Override
        public int getAmount(@NotNull FluidState fluidState) {
            return fluidState.getValue(LEVEL);
        }
    }

    public static class Source extends ImagPhaseFluid {
        @Override
        public boolean isSource(@NotNull FluidState fluidState) {
            return true;
        }

        @Override
        public int getAmount(@NotNull FluidState fluidState) {
            return 8;
        }
    }
}