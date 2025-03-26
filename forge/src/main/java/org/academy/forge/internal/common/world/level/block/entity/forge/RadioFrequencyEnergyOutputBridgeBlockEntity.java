package org.academy.forge.internal.common.world.level.block.entity.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RadioFrequencyEnergyOutputBridgeBlockEntity extends BlockEntity {
    public static final int CAPACITY = 40000;
    public static final int MAX_TRANSFER = 32;

    private final EnergyStorage energyStorage = new EnergyStorage(CAPACITY, MAX_TRANSFER, MAX_TRANSFER);
    private final LazyOptional<EnergyStorage> lazyEnergy = LazyOptional.of(() -> energyStorage);

    public RadioFrequencyEnergyOutputBridgeBlockEntity(BlockPos pos, BlockState blockState) {
        super(AcademyCraftBlockEntityTypesForge.RADIO_FREQUENCY_ENERGY_OUTPUT_BRIDGE, pos, blockState);
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) {
            return lazyEnergy.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("energy", energyStorage.serializeNBT());
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        if (tag.contains("energy")) {
            energyStorage.deserializeNBT(tag.get("energy"));
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        lazyEnergy.invalidate();
    }
}