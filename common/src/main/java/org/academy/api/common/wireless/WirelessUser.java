package org.academy.api.common.wireless;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public interface WirelessUser {
    Level getOwningLevel(); // Get the level this user is in

    BlockPos getPosition(); // Get the position of this user block

    /** @return The WirelessNode this user is currently connected to, or null if none. */
    @Nullable
    BlockPos getConnectedNodePosition();

    /** Tries to set the connected node. Called by the connection logic. */
    void setConnectedNodePosition(@Nullable BlockPos nodePos);

    /**
     * How much energy can be extracted from this user (Generator).
     * @param maxExtract Max energy requested.
     * @param simulate If true, do not actually change energy level.
     * @return Energy actually extracted.
     */
    double extractEnergy(double maxExtract, boolean simulate);

    /**
     * How much energy can be inserted into this user (Receiver).
     * @param maxReceive Max energy offered.
     * @param simulate If true, do not actually change energy level.
     * @return Energy actually accepted.
     */
    double receiveEnergy(double maxReceive, boolean simulate);

    // --- Optional for display/info ---
    double getEnergyStored();

    double getMaxEnergyStorage();
}