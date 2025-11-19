package org.academy.api.common.wireless;

import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

public interface WirelessUser {
    @Nullable
    BlockPos getConnectedNodePosition();

    void setConnectedNodePosition(@Nullable BlockPos nodePos);

    int extractEnergy(int maxExtract, boolean simulate);

    int receiveEnergy(int maxReceive, boolean simulate);

    int getEnergyStored();

    int getMaxEnergyStorage();
}