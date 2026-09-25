package org.academy.api.common.wireless;

import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

public interface WirelessUser {
    /**
     * Whether a connected node may pull energy from this device.
     */
    default boolean suppliesWirelessEnergy() {
        return true;
    }

    /**
     * Whether a connected node may push energy into this device.
     */
    default boolean acceptsWirelessEnergy() {
        return false;
    }

    @Nullable
    BlockPos getConnectedNodePosition();

    void setConnectedNodePosition(@Nullable BlockPos nodePos);

    int extractEnergy(int maxExtract, boolean simulate);

    int receiveEnergy(int maxReceive, boolean simulate);

    int getEnergyStored();

    int getMaxEnergyStorage();
}
