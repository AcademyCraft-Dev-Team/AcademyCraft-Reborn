package org.academy.api.server.wireless;

import net.minecraft.core.BlockPos;
import org.academy.api.common.wireless.WirelessNode;
import org.academy.api.common.wireless.WirelessUser;
import org.academy.internal.server.world.level.storage.WirelessNetworkData;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WirelessManagerTest {
    private static Map<WirelessUser, WirelessNetworkData.UserConfig> users(TestUser... users) {
        Map<WirelessUser, WirelessNetworkData.UserConfig> result = new LinkedHashMap<>();
        for (var user : users) {
            result.put(user, new WirelessNetworkData.UserConfig());
        }
        return result;
    }

    @Test
    void routesNewlyReceivedEnergyToConsumersInTheSameTick() {
        var node = new TestNode(0, 1_000, 100);
        var source = new TestUser(500, 500, true, false);
        var target = new TestUser(0, 500, false, true);

        WirelessManager.balanceEnergy(node, users(source, target));

        assertEquals(400, source.energy);
        assertEquals(100, target.energy);
        assertEquals(0, node.energy);
    }

    @Test
    void consumerEnergyIsNeverPulledBackIntoTheNode() {
        var node = new TestNode(0, 1_000, 100);
        var storage = new TestUser(1_000, 1_000, true, true);

        for (var tick = 0; tick < 20; tick++) {
            WirelessManager.balanceEnergy(node, users(storage));
        }

        assertEquals(0, node.energy);
        assertEquals(1_000, storage.energy);
    }

    @Test
    void redistributesBandwidthLeftOverBySmallTargets() {
        var node = new TestNode(100, 1_000, 100);
        var smallTarget = new TestUser(0, 10, false, true);
        var largeTarget = new TestUser(0, 1_000, false, true);

        WirelessManager.balanceEnergy(node, users(smallTarget, largeTarget));

        assertEquals(10, smallTarget.energy);
        assertEquals(90, largeTarget.energy);
        assertEquals(0, node.energy);
    }

    @Test
    void movingAUserRemovesItsPreviousSavedConnection() {
        var data = new WirelessNetworkData();
        var firstNode = new BlockPos(0, 64, 0);
        var secondNode = new BlockPos(10, 64, 0);
        var user = new BlockPos(5, 64, 0);
        data.registerNode(firstNode, "first", "", 32, 8);
        data.registerNode(secondNode, "second", "", 32, 8);

        assertTrue(data.connectUserToNode(firstNode, user));
        assertTrue(data.connectUserToNode(secondNode, user));

        assertTrue(data.getNodeConfig(firstNode).connectedUsers.isEmpty());
        assertTrue(data.getNodeConfig(secondNode).connectedUsers.containsKey(user));
        assertTrue(data.disconnectUserFromAllNodes(user));
        assertTrue(data.getNodeConfig(secondNode).connectedUsers.isEmpty());
    }

    private static final class TestNode implements WirelessNode {
        private final int maxEnergy;
        private final int transferRate;
        private int energy;

        private TestNode(int energy, int maxEnergy, int transferRate) {
            this.energy = energy;
            this.maxEnergy = maxEnergy;
            this.transferRate = transferRate;
        }

        @Override
        public int getEnergyStored() {
            return energy;
        }

        @Override
        public void setEnergyStored(int energy) {
            this.energy = Math.clamp(energy, 0, maxEnergy);
        }

        @Override
        public int getMaxEnergyStorage() {
            return maxEnergy;
        }

        @Override
        public int getEnergyTransferRate() {
            return transferRate;
        }

        @Override
        public int extractFromUser(WirelessUser user, int maxAmount, boolean simulate) {
            return user.extractEnergy(maxAmount, simulate);
        }

        @Override
        public int insertIntoUser(WirelessUser user, int maxAmount, boolean simulate) {
            return user.receiveEnergy(maxAmount, simulate);
        }
    }

    private static final class TestUser implements WirelessUser {
        private final int maxEnergy;
        private final boolean canExtract;
        private final boolean canReceive;
        private int energy;

        private TestUser(int energy, int maxEnergy, boolean canExtract, boolean canReceive) {
            this.energy = energy;
            this.maxEnergy = maxEnergy;
            this.canExtract = canExtract;
            this.canReceive = canReceive;
        }

        @Override
        public boolean suppliesWirelessEnergy() {
            return canExtract;
        }

        @Override
        public boolean acceptsWirelessEnergy() {
            return canReceive;
        }

        @Override
        public BlockPos getConnectedNodePosition() {
            return null;
        }

        @Override
        public void setConnectedNodePosition(BlockPos nodePos) {
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            if (!canExtract) return 0;
            var extracted = Math.min(maxExtract, energy);
            if (!simulate) energy -= extracted;
            return extracted;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (!canReceive) return 0;
            var received = Math.min(maxReceive, maxEnergy - energy);
            if (!simulate) energy += received;
            return received;
        }

        @Override
        public int getEnergyStored() {
            return energy;
        }

        @Override
        public int getMaxEnergyStorage() {
            return maxEnergy;
        }
    }
}
