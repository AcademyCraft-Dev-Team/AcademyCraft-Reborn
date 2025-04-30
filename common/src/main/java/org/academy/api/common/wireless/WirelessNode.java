package org.academy.api.common.wireless;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.academy.internal.server.world.level.storage.WorldData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface WirelessNode {
    int getRadius();

    List<BlockPos> getConnectedUserPositions();

    Level getOwningLevel();

    BlockPos getPosition();

    int getEnergyStored();

    void setEnergyStored(int energy);

    int getMaxEnergyStorage();

    int getEnergyTransferRate();

    int extractFromUser(WirelessUser user, int maxAmount, boolean simulate);

    int insertIntoUser(WirelessUser user, int maxAmount, boolean simulate);

    default boolean balanceEnergy(WorldData.WirelessNetworkData.NodeConfig nodeConfig) {
        int transferRate = getEnergyTransferRate();
        int receiveBudget = transferRate / 2;
        int sendBudget = transferRate - receiveBudget;
        boolean changed = false;

        int energyStored = getEnergyStored();
        int maxEnergy = getMaxEnergyStorage();

        Level level = getOwningLevel();
        if (level == null) return false;

        List<BlockPos> connectedUserPositions = new ArrayList<>(nodeConfig.connectedUsers.keySet());
        if (connectedUserPositions.isEmpty()) return false;

        Map<BlockPos, WirelessUser> userMap = new HashMap<>();
        Map<BlockPos, WorldData.WirelessNetworkData.UserConfig> userConfigMap = nodeConfig.connectedUsers;

        for (BlockPos userPos : connectedUserPositions) {
            BlockEntity userBE = level.getBlockEntity(userPos);
            if (userBE instanceof WirelessUser user) {
                userMap.put(userPos, user);
            }
        }

        double totalWeight = userConfigMap.values().stream().mapToDouble(WorldData.WirelessNetworkData.UserConfig::getWeight).sum();

        if (totalWeight > 0) {
            for (BlockPos userPos : connectedUserPositions) {
                if (energyStored >= maxEnergy || receiveBudget <= 0) break;

                WirelessUser user = userMap.get(userPos);
                if (user == null) continue;

                WorldData.WirelessNetworkData.UserConfig userConfig = userConfigMap.get(userPos);
                double weight = userConfig != null ? userConfig.getWeight() : 0.0;
                int userBudget = (int) Math.floor((weight / totalWeight) * receiveBudget);
                int maxPull = Math.min(Math.min(maxEnergy - energyStored, userBudget), transferRate);

                if (maxPull > 0) {
                    int extracted = extractFromUser(user, maxPull, false);
                    if (extracted > 0) {
                        energyStored += extracted;
                        receiveBudget -= extracted;
                        changed = true;
                    }
                }
            }

            if (changed) setEnergyStored(energyStored);
        }

        totalWeight = userConfigMap.values().stream().mapToDouble(WorldData.WirelessNetworkData.UserConfig::getWeight).sum();

        if (totalWeight > 0) {
            for (BlockPos userPos : connectedUserPositions) {
                if (energyStored <= 0 || sendBudget <= 0) break;

                WirelessUser user = userMap.get(userPos);
                if (user == null) continue;

                WorldData.WirelessNetworkData.UserConfig userConfig = userConfigMap.get(userPos);
                double weight = userConfig != null ? userConfig.getWeight() : 0.0;
                int userBudget = (int) Math.floor((weight / totalWeight) * sendBudget);
                int maxPush = Math.min(Math.min(energyStored, userBudget), transferRate);

                if (maxPush > 0) {
                    int accepted = insertIntoUser(user, maxPush, false);
                    if (accepted > 0) {
                        energyStored -= accepted;
                        sendBudget -= accepted;
                        changed = true;
                    }
                }
            }

            if (changed) setEnergyStored(energyStored);
        }
        return changed;
    }
}