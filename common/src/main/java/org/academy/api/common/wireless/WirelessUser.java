package org.academy.api.common.wireless;

import org.jetbrains.annotations.Nullable;

public interface WirelessUser {
    @Nullable
    WirelessNode getWirelessNode();
    void setWirelessNode(@Nullable WirelessNode wirelessNode);
    String getName();
    int getEnergyStorage();
    int getMaxEnergyStorage();
    void setEnergyStorage(int energyStorage);
}