package org.academy.api.common.wireless;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.List;

public interface WirelessNode {
    String getNodeName();

    boolean checkPassword(String passwordAttempt);

    int getRadius();
    List<BlockPos> getConnectedUserPositions();

    int getMaxConnections();

    Level getOwningLevel();

    BlockPos getPosition();

    int getEnergyStored();

    void setEnergyStored(int energy);

    int getMaxEnergyStorage();

    int getEnergyTransferRate();

    int extractFromUser(WirelessUser user, int maxAmount, boolean simulate);

    int insertIntoUser(WirelessUser user, int maxAmount, boolean simulate);
}