package org.academy.internal.common.world.level.block.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.academy.internal.common.world.level.block.entity.forge.RadioFrequencyEnergyOutputBridgeBlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RadioFrequencyEnergyOutputBridgeBlock extends BaseEntityBlock {
    public RadioFrequencyEnergyOutputBridgeBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new RadioFrequencyEnergyOutputBridgeBlockEntity(pos, state);
    }
}