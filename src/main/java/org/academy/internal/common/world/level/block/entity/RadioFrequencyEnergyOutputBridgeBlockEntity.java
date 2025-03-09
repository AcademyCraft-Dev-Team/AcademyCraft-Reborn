package org.academy.internal.common.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import team.reborn.energy.api.base.SimpleEnergyStorage;

public class RadioFrequencyEnergyOutputBridgeBlockEntity extends BlockEntity {
    public SimpleEnergyStorage energyStorage = new SimpleEnergyStorage(160000L, 128, 128);

    public RadioFrequencyEnergyOutputBridgeBlockEntity(BlockPos pos, BlockState blockState) {
        super(AcademyCraftBlockEntityTypes.RADIO_FREQUENCY_ENERGY_OUTPUT_BRIDGE, pos, blockState);
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        tag.putLong("amount", energyStorage.getAmount());
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        energyStorage.amount = tag.getLong("amount");
    }
}