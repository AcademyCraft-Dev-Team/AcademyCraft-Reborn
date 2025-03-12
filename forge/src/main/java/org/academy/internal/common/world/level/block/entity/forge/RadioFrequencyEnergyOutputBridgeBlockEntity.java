package org.academy.internal.common.world.level.block.entity.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import org.academy.forge.AcademyCraftBlockEntityTypesImpl;
import org.jetbrains.annotations.NotNull;

public class RadioFrequencyEnergyOutputBridgeBlockEntity extends BlockEntity {
    public EnergyStorage energyStorage = new EnergyStorage(16000, 128, 128);

    public RadioFrequencyEnergyOutputBridgeBlockEntity(BlockPos pos, BlockState blockState) {
        super(AcademyCraftBlockEntityTypesImpl.RADIO_FREQUENCY_ENERGY_OUTPUT_BRIDGE, pos, blockState);
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap) {
        if (cap == ForgeCapabilities.ENERGY) {
            return LazyOptional.of(() -> energyStorage).cast();
        } else {
            return LazyOptional.empty();
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        energyStorage.deserializeNBT(tag.get("energy"));
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        tag.put("energy", energyStorage.serializeNBT());
    }
}