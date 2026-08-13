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
    private static final double SURFACE_INSET = 0.025;

    @Override
    public FluidType getFluidType() {
        return Fluids.IMAG_PHASE_TYPE.get();
    }

    @Override
    protected void animateTick(Level level, BlockPos pos, FluidState state, RandomSource random) {
        double height = Math.max(0.125, state.getOwnHeight());
        int volumeCount = random.nextInt(2, 5);
        for (int i = 0; i < volumeCount; i++) {
            spawnParticle(
                    level,
                    pos.getX() + random.nextDouble(),
                    pos.getY() + SURFACE_INSET + random.nextDouble() * Math.max(0.01, height - SURFACE_INSET * 2.0),
                    pos.getZ() + random.nextDouble()
            );
        }

        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            if (isSame(level.getFluidState(neighborPos).getType())
                    || level.getBlockState(neighborPos)
                    .isFaceSturdy(level, neighborPos, direction.getOpposite())
                    || random.nextFloat() >= 0.55F) {
                continue;
            }
            spawnSurfaceParticle(level, pos, direction, height, random);
        }
    }

    private static void spawnSurfaceParticle(
            Level level,
            BlockPos pos,
            Direction direction,
            double height,
            RandomSource random
    ) {
        double x = pos.getX() + SURFACE_INSET + random.nextDouble() * (1.0 - SURFACE_INSET * 2.0);
        double y = pos.getY() + SURFACE_INSET
                + random.nextDouble() * Math.max(0.01, height - SURFACE_INSET * 2.0);
        double z = pos.getZ() + SURFACE_INSET + random.nextDouble() * (1.0 - SURFACE_INSET * 2.0);
        switch (direction) {
            case UP -> y = pos.getY() + height - SURFACE_INSET;
            case DOWN -> y = pos.getY() + SURFACE_INSET;
            case NORTH -> z = pos.getZ() + SURFACE_INSET;
            case SOUTH -> z = pos.getZ() + 1.0 - SURFACE_INSET;
            case WEST -> x = pos.getX() + SURFACE_INSET;
            case EAST -> x = pos.getX() + 1.0 - SURFACE_INSET;
        }
        spawnParticle(level, x, y, z);
    }

    private static void spawnParticle(Level level, double x, double y, double z) {
        level.addParticle(ParticleTypes.IMAG_PHASE_FLUID.get(), x, y, z, 0.0, 0.0, 0.0);
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
