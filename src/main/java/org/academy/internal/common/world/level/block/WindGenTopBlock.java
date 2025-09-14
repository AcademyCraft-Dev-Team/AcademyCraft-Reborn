package org.academy.internal.common.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.academy.internal.common.world.item.Items;
import org.academy.internal.common.world.level.block.entity.WindGenTopBlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class WindGenTopBlock extends MultiBlock {
    public static final MapCodec<WindGenTopBlock> CODEC = simpleCodec(WindGenTopBlock::new);
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final List<Vec3i> SUB_BLOCKS = List.of(
            new Vec3i(0, 0, -1)
    );
    private final Function<BlockState, VoxelShape> shapes;

    public WindGenTopBlock(BlockBehaviour.Properties properties) {
        super(properties.noOcclusion());
        shapes = makeShapes();
    }

    private Function<BlockState, VoxelShape> makeShapes() {
        var terminal = Block.box(4, 4, 6, 12, 12, 9);
        var axon = Block.box(5, 5, 9, 11, 11, 12);
        var main = Block.box(1, 1, 12, 15, 15, 29);

        var mainLD = Block.box(0, 0, 14, 3, 3, 30);
        var mainL = Block.box(0, 0, 11, 3, 16, 14);
        var mainLT = Block.box(0, 13, 14, 3, 16, 30);

        var mainRD = Block.box(13, 0, 14, 16, 3, 30);
        var mainR = Block.box(13, 0, 11, 16, 16, 14);
        var mainRT = Block.box(13, 13, 14, 16, 16, 30);

        var tail = Block.box(2, 3, 29, 14, 13, 32);

        var all = Shapes.or(axon, terminal, main, mainLD, mainL, mainLT, mainRD, mainR, mainRT, tail)
                .move(0, 0, 0.25f);

        var map = Map.of(
                MultiBlockType.MAIN,
                Shapes.rotateHorizontal(all.move(0, 0, -1)),
                MultiBlockType.SUBJECT,
                Shapes.rotateHorizontal(all)
        );

        return getShapeForEachState((blockState) -> map.get(blockState.getValue(TYPE)).get(blockState.getValue(FACING)));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (state.getValue(TYPE) == MultiBlockType.MAIN) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }

            if (getMainBlockEntity(level, pos) instanceof WindGenTopBlockEntity windGenTopBlockEntity) {
                var hand = player.getUsedItemHand();
                var stackInSlot = windGenTopBlockEntity.getItem(0);

                if (player.isShiftKeyDown() && !stackInSlot.isEmpty()) {
                    player.setItemInHand(hand, stackInSlot.copy());
                    windGenTopBlockEntity.setItem(0, ItemStack.EMPTY);
                    return InteractionResult.CONSUME;
                }
            }
        }
        return super.useWithoutItem(state, level, pos, player, hitResult);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (state.getValue(TYPE) == MultiBlockType.MAIN) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }

            if (getMainBlockEntity(level, pos) instanceof WindGenTopBlockEntity windGenTopBlockEntity) {
                ItemStack stackInHand = player.getItemInHand(hand);
                ItemStack stackInSlot = windGenTopBlockEntity.getItem(0);

                if (stackInSlot.isEmpty() && stackInHand.getItem() == Items.WIND_GEN_FAN_ITEM.get()) {
                    windGenTopBlockEntity.setItem(0, stackInHand.copyWithCount(1));
                    stackInHand.shrink(1);
                    return InteractionResult.CONSUME;
                }
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
        return pState.setValue(FACING, pRotation.rotate(pState.getValue(FACING)));
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext pContext) {
        return this.defaultBlockState().setValue(FACING, pContext.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.apply(state);
    }

    @Override
    public List<Vec3i> getSubBlocks() {
        return SUB_BLOCKS;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        builder.add(FACING, TYPE);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WindGenTopBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return (level1, pos, state1, blockEntity) -> {
            if (blockEntity instanceof WindGenTopBlockEntity windGenTopBlockEntity) {
                windGenTopBlockEntity.tick();
            }
        };
    }
}