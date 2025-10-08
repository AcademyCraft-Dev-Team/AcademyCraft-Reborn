package org.academy.internal.common.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public final class BuddingImagiphaseAmethystBlock extends ImagiphaseAmethystBlock {
    public static final MapCodec<BuddingImagiphaseAmethystBlock> CODEC = simpleCodec(BuddingImagiphaseAmethystBlock::new);
    private static final Direction[] DIRECTIONS = Direction.values();

    @Override
    public MapCodec<BuddingImagiphaseAmethystBlock> codec() {
        return CODEC;
    }

    public BuddingImagiphaseAmethystBlock(BlockBehaviour.Properties properties) {
        super(properties.mapColor(MapColor.COLOR_PURPLE)
                .randomTicks()
                .strength(1.5F)
                .sound(SoundType.AMETHYST)
                .requiresCorrectToolForDrops()
                .pushReaction(PushReaction.DESTROY));
    }

    /**
     * Performs a random tick on a block.
     */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(5) == 0) {
            Direction direction = DIRECTIONS[random.nextInt(DIRECTIONS.length)];
            BlockPos blockpos = pos.relative(direction);
            BlockState blockstate = level.getBlockState(blockpos);
            Block block = null;
            if (canClusterGrowAtState(blockstate)) {
                block = Blocks.SMALL_IMAGIPHASE_AMETHYST_BUD.get();
            } else if (blockstate.is(Blocks.SMALL_IMAGIPHASE_AMETHYST_BUD) && blockstate.getValue(AmethystClusterBlock.FACING) == direction) {
                block = Blocks.MEDIUM_IMAGIPHASE_AMETHYST_BUD.get();
            } else if (blockstate.is(Blocks.MEDIUM_IMAGIPHASE_AMETHYST_BUD.get()) && blockstate.getValue(AmethystClusterBlock.FACING) == direction) {
                block = Blocks.LARGE_IMAGIPHASE_AMETHYST_BUD.get();
            } else if (blockstate.is(Blocks.LARGE_IMAGIPHASE_AMETHYST_BUD.get()) && blockstate.getValue(AmethystClusterBlock.FACING) == direction) {
                block = Blocks.IMAGIPHASE_AMETHYST_CLUSTER.get();
            }

            if (block != null) {
                BlockState blockstate1 = block.defaultBlockState()
                        .setValue(AmethystClusterBlock.FACING, direction)
                        .setValue(AmethystClusterBlock.WATERLOGGED, blockstate.getFluidState().getType() == Fluids.WATER);
                level.setBlockAndUpdate(blockpos, blockstate1);
            }
        }
    }

    public static boolean canClusterGrowAtState(BlockState state) {
        return state.isAir() || state.is(net.minecraft.world.level.block.Blocks.WATER) && state.getFluidState().getAmount() == 8;
    }
}
