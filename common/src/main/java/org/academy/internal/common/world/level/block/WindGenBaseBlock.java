package org.academy.internal.common.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.academy.internal.common.world.level.block.entity.BlockEntityTypes;
import org.academy.internal.common.world.level.block.entity.WindGenBaseBlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("deprecation")
public class WindGenBaseBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public WindGenBaseBlock(Properties properties) {
        super(properties.noOcclusion());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new WindGenBaseBlockEntity(BlockEntityTypes.WIND_GEN_BASE_BLOCK_ENTITY_BLOCK_ENTITY_TYPE, pos, state);
    }

    @Override
    public @NotNull BlockState rotate(BlockState pState, Rotation pRotation) {
        return pState.setValue(FACING, pRotation.rotate(pState.getValue(FACING)));
    }

    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext pContext) {
        return this.defaultBlockState().setValue(FACING, pContext.getHorizontalDirection());
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> blockEntityType) {
        return (level1, pos, state1, blockEntity) -> {
            if (blockEntity instanceof WindGenBaseBlockEntity windGenBaseBlockEntity) {
                windGenBaseBlockEntity.ticks++;
                if (level1.isClientSide()) {
                    if (windGenBaseBlockEntity.active) {
                        windGenBaseBlockEntity.shuttingState.stop();
                        windGenBaseBlockEntity.activeState.startIfStopped(windGenBaseBlockEntity.ticks);
                    } else {
                        windGenBaseBlockEntity.activeState.stop();
                        windGenBaseBlockEntity.shuttingState.startIfStopped(windGenBaseBlockEntity.ticks);
                    }
                }
            }
        };
    }
}