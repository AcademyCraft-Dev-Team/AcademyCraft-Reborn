package org.academy.internal.common.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.core.particles.ParticleTypes;

public final class BuddingImagiphaseAmethystBlock extends ImagiphaseAmethystBlock {
    public static final MapCodec<BuddingImagiphaseAmethystBlock> CODEC = simpleCodec(BuddingImagiphaseAmethystBlock::new);
    private static final Direction[] DIRECTIONS = Direction.values();
    public static final BooleanProperty CATALYZING = BooleanProperty.create("catalyzing");

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
        registerDefaultState(stateDefinition.any().setValue(CATALYZING, false));
    }

    /**
     * Performs a random tick on a block.
     */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(5) == 0) {
            var direction = DIRECTIONS[random.nextInt(DIRECTIONS.length)];
            var blockpos = pos.relative(direction);
            var blockstate = level.getBlockState(blockpos);
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
                var blockstate1 = block.defaultBlockState()
                        .setValue(AmethystClusterBlock.FACING, direction)
                        .setValue(AmethystClusterBlock.WATERLOGGED, blockstate.getFluidState().getType() == Fluids.WATER);
                level.setBlockAndUpdate(blockpos, blockstate1);
            }
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(CATALYZING)) {
            var x = pos.getX();
            var y = pos.getY();
            var z = pos.getZ();

            var particlesPerEdge = 5;
            var edges = new Vec3[][]{
                    {new Vec3(x, y, z), new Vec3(x, y + 1, z)},
                    {new Vec3(x + 1, y, z), new Vec3(x + 1, y + 1, z)},
                    {new Vec3(x, y, z + 1), new Vec3(x, y + 1, z + 1)},
                    {new Vec3(x + 1, y, z + 1), new Vec3(x + 1, y + 1, z + 1)},

                    {new Vec3(x, y, z), new Vec3(x + 1, y, z)},
                    {new Vec3(x, y, z + 1), new Vec3(x + 1, y, z + 1)},
                    {new Vec3(x, y + 1, z), new Vec3(x + 1, y + 1, z)},
                    {new Vec3(x, y + 1, z + 1), new Vec3(x + 1, y + 1, z + 1)},

                    {new Vec3(x, y, z), new Vec3(x, y, z + 1)},
                    {new Vec3(x + 1, y, z), new Vec3(x + 1, y, z + 1)},
                    {new Vec3(x, y + 1, z), new Vec3(x, y + 1, z + 1)},
                    {new Vec3(x + 1, y + 1, z), new Vec3(x + 1, y + 1, z + 1)},
            };

            for (var edge : edges) {
                var start = edge[0];
                var end = edge[1];
                for (var p = 0; p < particlesPerEdge; p++) {
                    var t = (double) p / (particlesPerEdge - 1);
                    var px = start.x + (end.x - start.x) * t;
                    var py = start.y + (end.y - start.y) * t + 0.1;
                    var pz = start.z + (end.z - start.z) * t;
                    level.addParticle(ParticleTypes.IMAG_PHASE_FLUID.get(),
                            px, py, pz,
                            0, 0, 0);
                }
            }
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(CATALYZING));
    }

    public static boolean canClusterGrowAtState(BlockState state) {
        return state.isAir() || state.is(net.minecraft.world.level.block.Blocks.WATER) && state.getFluidState().getAmount() == 8;
    }
}
