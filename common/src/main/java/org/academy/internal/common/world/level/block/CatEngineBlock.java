package org.academy.internal.common.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import org.academy.internal.common.world.level.block.entity.BlockEntityTypes;
import org.academy.internal.common.world.level.block.entity.CatEngineBlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author cnlimiter
 */
public class CatEngineBlock extends BaseEntityBlock {
    public CatEngineBlock() {
        super(Properties.of()
                .mapColor(MapColor.STONE)
                .sound(SoundType.STONE)
                .noOcclusion()
                .strength(20.0f)
                .requiresCorrectToolForDrops()
        );
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos blockPos, @NotNull BlockState blockState) {
        return new CatEngineBlockEntity(blockPos, blockState);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> blockEntityType) {
        return level.isClientSide() ? createTickerHelper(blockEntityType, BlockEntityTypes.CAT_ENGINE, CatEngineBlockEntity::tickAnim) : null;
    }

}
