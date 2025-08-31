package org.academy.internal.common.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import org.academy.internal.common.world.level.block.entity.BlockEntityTypes;
import org.academy.internal.common.world.level.block.entity.CatEngineBlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * @author cnlimiter
 */
public class CatEngineBlock extends BaseEntityBlock {
    public static final MapCodec<CatEngineBlock> CODEC = simpleCodec(CatEngineBlock::new);

    public CatEngineBlock(Properties properties) {
        super(properties
                .mapColor(MapColor.STONE)
                .sound(SoundType.STONE)
                .noOcclusion()
                .strength(20.0f)
                .requiresCorrectToolForDrops()
        );
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return new CatEngineBlockEntity(blockPos, blockState);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return level.isClientSide() ? createTickerHelper(blockEntityType, BlockEntityTypes.CAT_ENGINE.get(), CatEngineBlockEntity::tickAnim) : null;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }
}
