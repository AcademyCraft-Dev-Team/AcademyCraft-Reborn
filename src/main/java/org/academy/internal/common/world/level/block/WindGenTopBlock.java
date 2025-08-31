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
import org.academy.internal.common.world.item.Items;
import org.academy.internal.common.world.level.block.entity.WindGenTopBlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class WindGenTopBlock extends MultiBlock {
    public static final MapCodec<WindGenTopBlock> CODEC = simpleCodec(WindGenTopBlock::new);
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final List<Vec3i> SUB_BLOCKS = List.of(
            new Vec3i(0, 0, 1),
            new Vec3i(0, 0, -1)
    );

    public WindGenTopBlock(BlockBehaviour.Properties properties) {
        super(properties.noOcclusion());
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