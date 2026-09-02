package org.academy.internal.common.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.academy.internal.common.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

/**
 * Imag-phase liquid can only be collected by the dedicated empty unit item.
 */
public final class ImagPhaseLiquidBlock extends LiquidBlock {
    public static final MapCodec<LiquidBlock> CODEC = simpleCodec(ImagPhaseLiquidBlock::new);

    public ImagPhaseLiquidBlock(BlockBehaviour.Properties properties) {
        super(Fluids.IMAG_PHASE.get(), properties);
    }

    @Override
    public MapCodec<LiquidBlock> codec() {
        return CODEC;
    }

    @Override
    public ItemStack pickupBlock(
            @Nullable LivingEntity user,
            LevelAccessor level,
            BlockPos pos,
            BlockState state
    ) {
        return ItemStack.EMPTY;
    }

    public ItemStack pickupWithEmptyUnit(LevelAccessor level, BlockPos pos, BlockState state) {
        if (state.getBlock() != this || state.getValue(LEVEL) != 0
                || !state.getFluidState().isSourceOfType(Fluids.IMAG_PHASE.get())) {
            return ItemStack.EMPTY;
        }
        level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 11);
        return new ItemStack(org.academy.internal.common.world.item.Items.IMAG_PHASE_UNIT.get());
    }
}
