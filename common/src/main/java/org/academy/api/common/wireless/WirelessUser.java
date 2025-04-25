package org.academy.api.common.wireless;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public interface WirelessUser {
    Level getOwningLevel();

    BlockPos getPosition();
    @Nullable
    BlockPos getConnectedNodePosition();

    void setConnectedNodePosition(@Nullable BlockPos nodePos);

    int extractEnergy(int maxExtract, boolean simulate);

    int receiveEnergy(int maxReceive, boolean simulate);

    int getEnergyStored();

    int getMaxEnergyStorage();
}