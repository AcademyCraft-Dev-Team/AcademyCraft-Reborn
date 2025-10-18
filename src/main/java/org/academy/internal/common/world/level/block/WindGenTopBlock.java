package org.academy.internal.common.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.academy.internal.common.world.item.Items;
import org.academy.internal.common.world.level.block.entity.BlockEntityTypes;
import org.academy.internal.common.world.level.block.entity.WindGenTopBlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public final class WindGenTopBlock extends BaseEntityBlock {
    public static final MapCodec<WindGenTopBlock> CODEC = simpleCodec(WindGenTopBlock::new);
    private final Function<BlockState, VoxelShape> shapes;

    public WindGenTopBlock(BlockBehaviour.Properties properties) {
        super(properties.noOcclusion());
        shapes = makeShapes();
    }

    private Function<BlockState, VoxelShape> makeShapes() {
        var terminal = box(4, 4, 6, 12, 12, 9);
        var axon = box(5, 5, 9, 11, 11, 12);
        var main = box(1, 1, 12, 15, 15, 29);

        var mainLD = box(0, 0, 14, 3, 3, 30);
        var mainL = box(0, 0, 11, 3, 16, 14);
        var mainLT = box(0, 13, 14, 3, 16, 30);

        var mainRD = box(13, 0, 14, 16, 3, 30);
        var mainR = box(13, 0, 11, 16, 16, 14);
        var mainRT = box(13, 13, 14, 16, 16, 30);

        var tail = box(2, 3, 29, 14, 13, 32);

        var all = Shapes.or(axon, terminal, main, mainLD, mainL, mainLT, mainRD, mainR, mainRT, tail).move(0, 0, 1 / 8f);

        return getShapeForEachState((blockState) -> Shapes.rotateHorizontal(all.move(0, 0, -1)).get(blockState.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (level.getBlockEntity(pos) instanceof WindGenTopBlockEntity windGenTopBlockEntity) {
            var hand = player.getUsedItemHand();
            var stackInSlot = windGenTopBlockEntity.getItem(0);

            if (player.isShiftKeyDown() && !stackInSlot.isEmpty()) {
                player.setItemInHand(hand, stackInSlot.copy());
                windGenTopBlockEntity.setItem(0, ItemStack.EMPTY);
                return InteractionResult.CONSUME;
            }
        }
        return super.useWithoutItem(state, level, pos, player, hitResult);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof WindGenTopBlockEntity windGenTopBlockEntity) {
            var stackInHand = player.getItemInHand(hand);
            var stackInSlot = windGenTopBlockEntity.getItem(0);

            if (stackInSlot.isEmpty() && stackInHand.getItem() == Items.WIND_GEN_FAN_ITEM.get()) {
                windGenTopBlockEntity.setItem(0, stackInHand.copyWithCount(1));
                stackInHand.shrink(1);
                return InteractionResult.CONSUME;
            }
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState rotate(BlockState pState, Rotation pRotation) {
        return pState.setValue(BlockStateProperties.HORIZONTAL_FACING, pRotation.rotate(pState.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext pContext) {
        return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, pContext.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.apply(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WindGenTopBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return createTickerHelper(blockEntityType, BlockEntityTypes.WIND_GEN_TOP.get(), WindGenTopBlockEntity::tick);
    }
}