package org.academy.internal.common.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class AdvancedWirelessNodeBlockEntity extends WirelessNodeBlockEntity {
    private static final int MAX_ENERGY = 2_400_000;
    private static final int TRANSFER_RATE = 20000;

    public AdvancedWirelessNodeBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityTypes.ADVANCED_WIRELESS_NODE, pos, blockState);
    }

    @Override
    public int getMaxEnergyStorage() {
        return MAX_ENERGY;
    }

    @Override
    public int getEnergyTransferRate() {
        return 20;
    }
}