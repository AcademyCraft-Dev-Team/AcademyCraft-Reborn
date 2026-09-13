package org.academy.internal.common.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.academy.api.server.util.ServerPlayerUtil;
import org.academy.internal.common.world.inventory.SatelliteLaunchPadMenu;
import org.academy.internal.common.world.level.block.entity.BlockEntityTypes;
import org.academy.internal.common.world.level.block.entity.OwnedDevice;
import org.academy.internal.common.world.level.block.entity.SatelliteLaunchPadBlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * Single-block satellite launch pad (geo ~1.31×0.44×1.0; slight horizontal overhang accepted).
 * Coolant tank ({@code waterMb}) is authoritative; the liquid surface is drawn by the BER from
 * fill ratio. Vanilla waterlogged/fluid mesh is not used (it only shows full or empty).
 * Full-cube collision still blocks stray fluid from occupying the cell.
 */
public final class SatelliteLaunchPadBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    public static final String SCREEN = "satellite_launch_pad_screen";
    public static final MapCodec<SatelliteLaunchPadBlock> CODEC = simpleCodec(SatelliteLaunchPadBlock::new);
    /**
     * Placement-only flag: set when replacing a water source so {@link #setPlacedBy} can seed the tank.
     * Cleared by {@link #syncWaterlogged}; not used for rendering.
     */
    public static final IntegerProperty WATER_LEVEL = IntegerProperty.create("water_level", 0, 8);
    private static final VoxelShape SHAPE = Shapes.box(0.0, 0.0, 0.0, 1.0, 0.5, 1.0);

    public SatelliteLaunchPadBlock(Properties properties) {
        super(properties.mapColor(MapColor.METAL).sound(SoundType.METAL).noOcclusion());
        registerDefaultState(stateDefinition.any()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                .setValue(BlockStateProperties.WATERLOGGED, false)
                .setValue(WATER_LEVEL, 0));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player player) {
            OwnedDevice.assignPlacer(level.getBlockEntity(pos), player);
        }
        // Placement in water seeds WATER_LEVEL > 0 without WATERLOGGED (see getStateForPlacement).
        if (!level.isClientSide()
                && state.getValue(WATER_LEVEL) > 0
                && level.getBlockEntity(pos) instanceof SatelliteLaunchPadBlockEntity pad) {
            pad.absorbPlacementWater();
        }
    }

    @Override
    protected InteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult
    ) {
        if (level.getBlockEntity(pos) instanceof SatelliteLaunchPadBlockEntity pad) {
            if (stack.is(Items.WATER_BUCKET)) {
                // FAIL does not consumeAction — WaterBucket would then dump into the world ("overflow").
                return pad.tryFillWater(player, hand)
                        ? InteractionResult.SUCCESS
                        : InteractionResult.CONSUME;
            }
            if (stack.is(Items.BUCKET)) {
                return pad.tryDrainWater(player, hand)
                        ? InteractionResult.SUCCESS
                        : InteractionResult.CONSUME;
            }
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult
    ) {
        if (player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof SatelliteLaunchPadBlockEntity blockEntity) {
            var menuProvider = getMenuProvider(state, level, pos);
            ServerPlayerUtil.openMenuScreen(serverPlayer, menuProvider, SCREEN,
                    buf -> buf.writeBlockPos(blockEntity.getBlockPos()));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /**
     * Full cube so a waterlogged source cannot flow out (same trick as leaves).
     */
    @Override
    protected VoxelShape getCollisionShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context
    ) {
        return Shapes.block();
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        // Coolant is BER-only; vanilla fluid mesh cannot show intermediate tank levels here.
        return super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            LevelReader level,
            ScheduledTickAccess ticks,
            BlockPos pos,
            Direction direction,
            BlockPos neighborPos,
            BlockState neighborState,
            RandomSource random
    ) {
        // Do not schedule Fluids.WATER ticks: partial WATER_LEVEL is tank-driven décor.
        // Vanilla flowing ticks would lower/spread the overlay independently of waterMb.
        return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    public boolean canPlaceLiquid(@Nullable LivingEntity living, BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
        // Coolant is filled via water bucket → BE tank, not free waterlogging.
        return false;
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluidState) {
        return false;
    }

    @Override
    public ItemStack pickupBlock(@Nullable LivingEntity living, LevelAccessor level, BlockPos pos, BlockState state) {
        // Empty bucket is handled by tryDrainWater on the BE (1 bucket at a time).
        return ItemStack.EMPTY;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.WATERLOGGED, WATER_LEVEL);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        var fluid = context.getLevel().getFluidState(context.getClickedPos());
        boolean water = fluid.is(Fluids.WATER);
        return defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, context.getHorizontalDirection().getOpposite())
                // Never waterlog: that mesh ignores WATER_LEVEL and always looks full.
                .setValue(BlockStateProperties.WATERLOGGED, false)
                // Placement absorb seeds tank via WATER_LEVEL flag; BER reads waterMb after sync.
                .setValue(WATER_LEVEL, water ? 1 : 0);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(
                BlockStateProperties.HORIZONTAL_FACING,
                rot.rotate(state.getValue(BlockStateProperties.HORIZONTAL_FACING))
        );
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> blockEntityType
    ) {
        return createTickerHelper(
                blockEntityType,
                BlockEntityTypes.SATELLITE_LAUNCH_PAD.get(),
                SatelliteLaunchPadBlockEntity::tick
        );
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SatelliteLaunchPadBlockEntity(pos, state);
    }

    @Override
    protected @Nullable MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof SatelliteLaunchPadBlockEntity blockEntity) {
            return new SimpleMenuProvider((containerId, playerInventory, player) ->
                    new SatelliteLaunchPadMenu(
                            containerId,
                            playerInventory,
                            ContainerLevelAccess.create(level, pos),
                            blockEntity
                    ), Component.translatable("block.academy.satellite_launch_pad")
            );
        }
        return null;
    }

    /**
     * Map tank millibuckets → legacy 0–8 steps (kept for any external callers / debug).
     */
    public static int fluidLevelForMb(int waterMb) {
        if (waterMb <= 0) {
            return 0;
        }
        return Mth.clamp(
                Mth.ceil(waterMb * 8.0 / SatelliteLaunchPadBlockEntity.WATER_CAPACITY_MB),
                1,
                8
        );
    }

    /**
     * Clear vanilla waterlogged/fluid props after tank changes. Visual fill is BER-driven from {@code waterMb}.
     */
    public static void syncWaterlogged(LevelAccessor level, BlockPos pos, int waterMb) {
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SatelliteLaunchPadBlock)) {
            return;
        }
        if (!state.getValue(BlockStateProperties.WATERLOGGED) && state.getValue(WATER_LEVEL) == 0) {
            return;
        }
        level.setBlock(
                pos,
                state.setValue(BlockStateProperties.WATERLOGGED, false).setValue(WATER_LEVEL, 0),
                Block.UPDATE_ALL
        );
    }
}
