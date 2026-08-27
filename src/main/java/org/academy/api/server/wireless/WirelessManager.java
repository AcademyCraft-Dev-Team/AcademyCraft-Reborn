package org.academy.api.server.wireless;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.api.common.wireless.*;
import org.academy.internal.server.world.level.storage.WirelessNetworkData;
import org.apache.commons.lang3.tuple.Pair;
import org.jspecify.annotations.Nullable;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.future.annotation.HandleFuture;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class WirelessManager {
    private static final Logger LOGGER = AcademyCraft.getLogger();

    private WirelessManager() {
    }

    public static void initServer() {
        MisakaNetworkServer.NETWORK_MANAGER.register(WirelessManager.class);
        MisakaNetworkServer.FUTURE_MANAGER.register(WirelessManager.class);
    }

    @HandleFuture
    public static GetAvailableNodesPacket.Response onGetAvailableNodes(GetAvailableNodesPacket payload) {
        var player = payload.getPacketListener().getPlayer();
        var level = player.level();
        var requesterPos = payload.getRequesterPos();
        return new GetAvailableNodesPacket.Response(getAvailableNodes(level, requesterPos));
    }

    @HandleFuture
    public static GetCurrentNodePacket.Response onGetCurrentNode(GetCurrentNodePacket payload) {
        var player = payload.getPacketListener().getPlayer();
        var level = player.level();
        var userPos = payload.getUserPos();
        var currentNode = getCurrentNode(level, userPos);
        return new GetCurrentNodePacket.Response(currentNode.getLeft(), currentNode.getRight());
    }

    @SubscribePacket
    public static void onConnectNode(ConnectNodePacket packet) {
        var player = packet.getPacketListener().getPlayer();
        var level = player.level();
        var userPos = packet.getUserPos();
        var targetNodeName = packet.getTargetNodeName();
        var passwordAttempt = packet.getPasswordAttempt();
        handleConnect(player, level, userPos, targetNodeName, passwordAttempt);
    }

    @SubscribePacket
    public static void onDisconnectNode(DisconnectNodePacket packet) {
        var player = packet.getPacketListener().getPlayer();
        var level = player.level();
        var userPos = packet.getUserPos();
        handleDisconnect(player, level, userPos);
    }

    @SubscribePacket
    public static void onSetNodeName(SetNodeNamePacket packet) {
        var player = packet.getPacketListener().getPlayer();
        var level = player.level();
        var nodePos = packet.getNodePos();
        var newName = packet.getNewName();
        setNodeName(player, level, nodePos, newName);
    }

    @SubscribePacket
    public static void onSetNodePass(SetNodePassPacket packet) {
        var player = packet.getPacketListener().getPlayer();
        var level = player.level();
        var nodePos = packet.getNodePos();
        var newPass = packet.getNewPass();
        setNodePass(player, level, nodePos, newPass);
    }

    public static void setNodeName(ServerPlayer player,
                                   ServerLevel level,
                                   BlockPos nodePos,
                                   String newName) {
        if (player.position().distanceToSqr(Vec3.atCenterOf(nodePos)) > 64.0) {
            return;
        }
        var data = WirelessNetworkData.get(level);

        var oldCfg = data.getNodeConfig(nodePos);
        if (oldCfg == null) {
            LOGGER.warn("Player {} tried to rename nonexistent node at {}",
                    player.getGameProfile().name(), nodePos);
            return;
        }

        var oldName = oldCfg.name;
        if (!data.renameNode(nodePos, newName)) {
            var existingNodePos = data.findNodePositionByName(newName);
            LOGGER.warn("Player {} tried to rename node at {} to '{}', but that name is already taken by node at {}",
                    player.getGameProfile().name(), nodePos, newName, existingNodePos);
            return;
        }

        LOGGER.debug("Player {} renamed node at {} from '{}' to '{}'",
                player.getGameProfile().name(), nodePos, oldName, newName);
    }

    public static void setNodePass(ServerPlayer player,
                                   ServerLevel level,
                                   BlockPos nodePos,
                                   String newPass) {
        if (player.position().distanceToSqr(Vec3.atCenterOf(nodePos)) > 64.0) {
            return;
        }
        var data = WirelessNetworkData.get(level);

        var cfg = data.getNodeConfig(nodePos);
        if (cfg == null) {
            LOGGER.warn("Player {} tried to change password of nonexistent node at {}",
                    player.getGameProfile().name(), nodePos);
            return;
        }

        cfg.password = newPass;
        data.setDirty();
        LOGGER.debug("Player {} changed password of node '{}' at {}",
                player.getGameProfile().name(), cfg.name, nodePos);
    }

    public static void handleConnect(ServerPlayer player, ServerLevel level, BlockPos userPos, String targetNodeName, String passwordAttempt) {
        var networkData = WirelessNetworkData.get(level);

        var nodePos = networkData.findNodePositionByName(targetNodeName);
        if (nodePos == null) {
            LOGGER.warn("Player {} failed connecting user at {}: Node '{}' not found.", player.getGameProfile().name(), userPos, targetNodeName);
            return;
        }

        var nodeConfig = networkData.getNodeConfig(nodePos);

        if (nodeConfig == null) {
            LOGGER.error("Node position {} found for '{}' but NodeConfig is missing!", nodePos, targetNodeName);
            return;
        }

        if (nodePos.equals(userPos)) {
            LOGGER.warn("Player {} tried to connect wireless node '{}' to itself.", player.getGameProfile().name(), targetNodeName);
            return;
        }

        var userBE = level.getBlockEntity(userPos);
        if (!(userBE instanceof WirelessUser wirelessUser)) {
            LOGGER.warn("Player {} tried to connect invalid block at {} to node '{}'. Block is not a WirelessUser.", player.getGameProfile().name(), userPos, targetNodeName);
            return;
        }

        if (!(level.getBlockEntity(nodePos) instanceof WirelessNode)) {
            LOGGER.warn("Player {} tried to connect user at {} to missing wireless node '{}' at {}.", player.getGameProfile().name(), userPos, targetNodeName, nodePos);
            return;
        }

        if (nodePos.distSqr(userPos) > (double) nodeConfig.radius * nodeConfig.radius) {
            LOGGER.warn("User at {} is too far from node '{}' (Radius: {}).", userPos, targetNodeName, nodeConfig.radius);
            return;
        }

        if (!nodeConfig.checkPassword(passwordAttempt)) {
            LOGGER.warn("Incorrect password provided by {} for node '{}' from user at {}.", player.getGameProfile().name(), targetNodeName, userPos);
            return;
        }

        if (!nodeConfig.connectedUsers.containsKey(userPos)
                && nodeConfig.connectedUsers.size() >= nodeConfig.maxConnections) {
            LOGGER.warn("Node '{}' has reached its maximum connection limit. User at {} cannot connect.", targetNodeName, userPos);
            return;
        }

        if (userBE instanceof WirelessNode && createsConnectionCycle(level, userPos, nodePos)) {
            LOGGER.warn("Connecting wireless node at {} to '{}' would create a cycle.", userPos, targetNodeName);
            return;
        }

        if (networkData.connectUserToNode(nodePos, userPos)) {
            wirelessUser.setConnectedNodePosition(nodePos);
            LOGGER.debug("User at {} successfully connected to node '{}' (at {}).", userPos, targetNodeName, nodePos);
        } else {
            LOGGER.warn("Failed connecting user {} to node '{}': Node likely full or user already connected elsewhere.", userPos, targetNodeName);
        }
    }

    private static boolean createsConnectionCycle(ServerLevel level, BlockPos userPos, BlockPos targetNodePos) {
        var visited = new HashSet<BlockPos>();
        var current = targetNodePos;
        while (current != null && visited.add(current)) {
            if (current.equals(userPos)) {
                return true;
            }
            var blockEntity = level.getBlockEntity(current);
            if (!(blockEntity instanceof WirelessUser wirelessUser)) {
                return false;
            }
            current = wirelessUser.getConnectedNodePosition();
        }
        return false;
    }

    public static void handleDisconnect(@Nullable ServerPlayer player, ServerLevel level, BlockPos userPos) {
        var userBE = level.getBlockEntity(userPos);
        if (!(userBE instanceof WirelessUser wirelessUser)) {
            var playerName = (player != null) ? player.getGameProfile().name() : "System";
            LOGGER.warn("{} tried to disconnect invalid block at {}.", playerName, userPos);
            return;
        }

        var connectedNodePosition = wirelessUser.getConnectedNodePosition();
        var networkData = WirelessNetworkData.get(level);
        var removedFromData = networkData.disconnectUserFromAllNodes(userPos);
        if (removedFromData) {
            LOGGER.debug("Successfully removed all SavedData associations for user {} (reported node: {}).", userPos, connectedNodePosition);
        } else {
            LOGGER.debug("User at {} had no SavedData association (reported node: {}).", userPos, connectedNodePosition);
        }

        wirelessUser.setConnectedNodePosition(null);
        LOGGER.debug("User at {} connection state (WirelessUser instance) cleared.", userPos);
    }

    public static List<String> getAvailableNodes(ServerLevel level, BlockPos requesterPos) {
        var data = WirelessNetworkData.get(level);
        var nodeNamesInRange = new ArrayList<String>();
        for (var entry : data.getAllNodes().entrySet()) {
            var nodePos = entry.getKey();
            var config = entry.getValue();
            if (!nodePos.equals(requesterPos)
                    && nodePos.distSqr(requesterPos) <= (double) config.radius * config.radius) {
                nodeNamesInRange.add(config.name);
            }
        }
        nodeNamesInRange.sort(String.CASE_INSENSITIVE_ORDER);
        return nodeNamesInRange;
    }

    public static Pair<Boolean, String> getCurrentNode(ServerLevel level, BlockPos userPos) {
        String currentNodeName = null;
        var be = level.getBlockEntity(userPos);
        if (be instanceof WirelessUser user) {
            var connectedNodePos = user.getConnectedNodePosition();
            if (connectedNodePos != null) {
                var data = WirelessNetworkData.get(level);
                var nodeConfig = data.getNodeConfig(connectedNodePos);
                if (nodeConfig != null) {
                    currentNodeName = nodeConfig.name;
                }
            }
        }
        if (currentNodeName == null) {
            currentNodeName = "None";
            return Pair.of(true, currentNodeName);
        } else {
            return Pair.of(false, currentNodeName);
        }
    }

    public static void balanceEnergy(
            WirelessNode node,
            Map<WirelessUser, WirelessNetworkData.UserConfig> userConfigMap
    ) {
        if (userConfigMap.isEmpty()) return;

        var transferRate = Math.max(0, node.getEnergyTransferRate());
        var maxEnergy = Math.max(0, node.getMaxEnergyStorage());
        var energyStored = Mth.clamp(node.getEnergyStored(), 0, maxEnergy);
        if (transferRate == 0 || maxEnergy == 0) return;

        var extractSources = new ArrayList<TransferCandidate>();
        var insertTargets = new ArrayList<TransferCandidate>();

        for (var entry : userConfigMap.entrySet()) {
            var user = entry.getKey();
            if (user == node) {
                continue;
            }
            var cfg = entry.getValue();
            var receiveWeight = cfg.receiveWeight();
            var sendWeight = cfg.sendWeight();

            // A consumer role wins if a device declares both capabilities. This keeps
            // internal machine extraction separate from network output and prevents
            // energy from bouncing back every tick.
            var acceptsEnergy = user.acceptsWirelessEnergy();
            var canExtract = !acceptsEnergy && user.suppliesWirelessEnergy()
                    ? node.extractFromUser(user, transferRate, true)
                    : 0;
            var canInsert = acceptsEnergy
                    ? node.insertIntoUser(user, transferRate, true)
                    : 0;

            canExtract = Mth.clamp(canExtract, 0, transferRate);
            canInsert = Mth.clamp(canInsert, 0, transferRate);

            if (canExtract > 0) {
                extractSources.add(new TransferCandidate(user, canExtract, receiveWeight));
            }
            if (canInsert > 0) {
                insertTargets.add(new TransferCandidate(user, canInsert, sendWeight));
            }
        }

        if (insertTargets.isEmpty() && extractSources.isEmpty()) return;

        var receiveBudget = Math.min(transferRate, maxEnergy - energyStored);
        var received = moveEnergy(extractSources, receiveBudget, node::extractFromUser);
        energyStored += received;

        var sendBudget = Math.min(transferRate, energyStored);
        var sent = moveEnergy(insertTargets, sendBudget, node::insertIntoUser);
        energyStored -= sent;

        node.setEnergyStored(energyStored);
    }

    private static int moveEnergy(List<TransferCandidate> candidates, int budget, EnergyMover mover) {
        if (budget <= 0 || candidates.isEmpty()) return 0;

        var remaining = budget;
        while (remaining > 0) {
            var totalWeight = 0.0;
            for (var candidate : candidates) {
                if (candidate.hasCapacity() && candidate.hasWeight()) {
                    totalWeight += candidate.weight;
                }
            }
            if (!(totalWeight > 0.0) || !Double.isFinite(totalWeight)) break;

            var roundBudget = remaining;
            var allocatedThisRound = 0;
            for (var candidate : candidates) {
                if (!candidate.hasCapacity() || !candidate.hasWeight()) continue;
                var share = Mth.floor(roundBudget * (candidate.weight / totalWeight));
                var allocated = Math.min(share, candidate.remainingCapacity());
                if (allocated <= 0) continue;
                candidate.allocated += allocated;
                remaining -= allocated;
                allocatedThisRound += allocated;
            }

            if (allocatedThisRound == 0) {
                for (var candidate : candidates) {
                    if (remaining == 0) break;
                    if (!candidate.hasCapacity() || !candidate.hasWeight()) continue;
                    candidate.allocated++;
                    remaining--;
                    allocatedThisRound++;
                }
            }
            if (allocatedThisRound == 0) break;
        }

        var movedTotal = 0;
        for (var candidate : candidates) {
            if (candidate.allocated == 0) continue;
            var moved = Mth.clamp(mover.move(candidate.user, candidate.allocated, false), 0, candidate.allocated);
            movedTotal += moved;
        }
        return movedTotal;
    }

    @FunctionalInterface
    private interface EnergyMover {
        int move(WirelessUser user, int amount, boolean simulate);
    }

    private static final class TransferCandidate {
        private final WirelessUser user;
        private final int capacity;
        private final double weight;
        private int allocated;

        private TransferCandidate(WirelessUser user, int capacity, double weight) {
            this.user = user;
            this.capacity = capacity;
            this.weight = weight;
        }

        private boolean hasCapacity() {
            return allocated < capacity;
        }

        private int remainingCapacity() {
            return capacity - allocated;
        }

        private boolean hasWeight() {
            return weight > 0.0 && Double.isFinite(weight);
        }
    }
}
