package org.academy.api.common.wireless;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.List;

public interface WirelessNode {

    // --- Configuration (Managed by SavedData/Synced) ---
    String getNodeName();

    boolean checkPassword(String passwordAttempt);

    int getRadius(); // Range of the node

    // --- Connections (Managed by SavedData) ---

    /** Returns an immutable list of connected user positions */
    List<BlockPos> getConnectedUserPositions();

    /** Max number of users this node can handle */
    int getMaxConnections();

    // --- Runtime State (Managed by BlockEntity) ---
    Level getOwningLevel(); // Get the level this node is in

    BlockPos getPosition(); // Get the position of this node block

    double getEnergyStored();

    void setEnergyStored(double energy); // Careful direct use

    double getMaxEnergyStorage();

    double getEnergyTransferRate(); // Max energy moved per tick per connection

    // --- Runtime Operations (Likely internal to ticker) ---

    /** Attempts to extract energy FROM a user at userPos */
    double extractFromUser(BlockPos userPos, double maxAmount, boolean simulate);

    /** Attempts to insert energy INTO a user at userPos */
    double insertIntoUser(BlockPos userPos, double maxAmount, boolean simulate);
}