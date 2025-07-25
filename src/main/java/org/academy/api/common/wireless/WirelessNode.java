package org.academy.api.common.wireless;

public interface WirelessNode {
    int getEnergyStored();

    void setEnergyStored(int energy);

    int getMaxEnergyStorage();

    int getEnergyTransferRate();

    int extractFromUser(WirelessUser user, int maxAmount, boolean simulate);

    int insertIntoUser(WirelessUser user, int maxAmount, boolean simulate);
}