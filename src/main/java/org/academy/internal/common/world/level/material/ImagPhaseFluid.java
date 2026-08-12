package org.academy.internal.common.world.level.material;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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
import net.neoforged.neoforge.fluids.FluidType;
import org.academy.internal.common.core.particles.ParticleTypes;
import org.academy.internal.common.world.item.Items;
import org.academy.internal.common.world.level.block.Blocks;

public abstract class ImagPhaseFluid extends FlowingFluid {
    @Override
    public FluidType getFluidType() {
        return Fluids.IMAG_PHASE_TYPE.get();
    }

    @Override
    protected void animateTick(Level level, BlockPos pos, FluidState state, RandomSource random) {
        int particleCount = random.nextInt(2, 5);
        double height = Math.max(0.125, state.getOwnHeight());
        for (int i = 0; i < particleCount; i++) {
            level.addParticle(
                    ParticleTypes.IMAG_PHASE_FLUID.get(),
                    pos.getX() + random.nextDouble(),
                    pos.getY() + random.nextDouble() * height,
                    pos.getZ() + random.nextDouble(),
                    0.0,
                    0.0,
                    0.0
            );
        }
    }

    @Override
    public Fluid getFlowing() {
        return Fluids.FLOWING_IMAG_PHASE.get();
    }

    @Override
    public Fluid getSource() {
        return Fluids.IMAG_PHASE.get();
    }

    @Override
    public boolean isSame(Fluid fluid) {
        return fluid == Fluids.IMAG_PHASE.get() || fluid == Fluids.FLOWING_IMAG_PHASE.get();
    }

    @Override
    protected boolean canConvertToSource(ServerLevel level) {
        return false;
    }

    @Override
    protected void beforeDestroyingBlock(LevelAccessor level, BlockPos pos, BlockState state) {
    }

    @Override
    protected int getSlopeFindDistance(LevelReader level) {
        return 4;
    }

    @Override
    protected int getDropOff(LevelReader level) {
        return 1;
    }

    @Override
    public Item getBucket() {
        return Items.IMAG_PHASE_UNIT.get();
    }

    @Override
    protected int getSpreadDelay(Level level, BlockPos pos, FluidState currentState, FluidState newState) {
        return 0;
    }

    @Override
    protected boolean canBeReplacedWith(
            FluidState state,
            BlockGetter level,
            BlockPos pos,
            Fluid incomingFluid,
            Direction direction
    ) {
        return false;
    }

    @Override
    public int getTickDelay(LevelReader level) {
        return 2;
    }

    @Override
    protected float getExplosionResistance() {
        return 50.0F;
    }

    @Override
    protected BlockState createLegacyBlock(FluidState state) {
        return Blocks.IMAG_PHASE.get().defaultBlockState().setValue(LiquidBlock.LEVEL, getLegacyLevel(state));
    }

    public static final class Flowing extends ImagPhaseFluid {
        @Override
        protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
            builder.add(LEVEL);
        }

        @Override
        public boolean isSource(FluidState state) {
            return false;
        }

        @Override
        public int getAmount(FluidState state) {
            return state.getValue(LEVEL);
        }
    }

    public static final class Source extends ImagPhaseFluid {
        @Override
        public boolean isSource(FluidState state) {
            return true;
        }

        @Override
        public int getAmount(FluidState state) {
            return 8;
        }
    }
}
