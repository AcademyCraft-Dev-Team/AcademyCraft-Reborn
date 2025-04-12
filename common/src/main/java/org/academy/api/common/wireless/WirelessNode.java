package org.academy.api.common.wireless;

import org.jetbrains.annotations.Nullable;

public interface WirelessNode {
    @Nullable
    WirelessMaster getWirelessMaster();
    String getName();
    int getEnergyStorage();
    int getMaxEnergyStorage();
    void setEnergyStorage(int energyStorage);
}