package org.academy.fabric.internal.common.world.level.block.entity.fabric;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import team.reborn.energy.api.base.SimpleEnergyStorage;

public class RadioFrequencyEnergyOutputBridgeBlockEntity extends BlockEntity {
    public SimpleEnergyStorage energyStorage = new SimpleEnergyStorage(40000, 32, 32) {
        @SuppressWarnings("UnstableApiUsage")
        @Override
        protected void onFinalCommit() {
            setChanged();
        }
    };

    public RadioFrequencyEnergyOutputBridgeBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityTypesFabric.RADIO_FREQUENCY_ENERGY_OUTPUT_BRIDGE, pos, blockState);
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