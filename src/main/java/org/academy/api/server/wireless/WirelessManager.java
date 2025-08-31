package org.academy.api.server.wireless;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftServer;
import org.academy.api.common.network.SubscribePacket;
import org.academy.api.common.network.future.HandlePayload;
import org.academy.api.common.wireless.*;
import org.academy.internal.server.world.level.storage.WirelessNetworkData;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class WirelessManager {
    public static void initServer() {
        AcademyCraftServer.NETWORK_MANAGER.registerPacketListener(WirelessManager.class);
        AcademyCraftServer.FUTURE_MANAGER.registerPayloadHandler(WirelessManager.class);
    }

    @HandlePayload
    public static GetAvailableNodesPacket.Response onGetAvailableNodes(GetAvailableNodesPacket payload) {
        var player = payload.getPacketListener().getPlayer();
        var level = player.level();
        var requesterPos = payload.requesterPos;
        return new GetAvailableNodesPacket.Response(getAvailableNodes(level, requesterPos));
    }

    @HandlePayload
    public static GetCurrentNodePacket.Response onGetCurrentNode(GetCurrentNodePacket payload) {
        var player = payload.getPacketListener().getPlayer();
        var level = player.level();
        var userPos = payload.userPos;
        var currentNode = getCurrentNode(level, userPos);
        return new GetCurrentNodePacket.Response(currentNode.getLeft(), currentNode.getRight());
    }

    @SubscribePacket
    public static void onConnectNode(ConnectNodePacket packet) {
        var player = packet.getPacketListener().getPlayer();
        var level = player.level();
        var userPos = packet.userPos;
        var targetNodeName = packet.targetNodeName;
        var passwordAttempt = packet.passwordAttempt;
        handleConnect(player, level, userPos, targetNodeName, passwordAttempt);
    }

    @SubscribePacket
    public static void onDisconnectNode(DisconnectNodePacket packet) {
        var player = packet.getPacketListener().getPlayer();
        var level = player.level();
        var userPos = packet.userPos;
        handleDisconnect(player, level, userPos);
    }

    @SubscribePacket
    public static void onSetNodeName(SetNodeNamePacket packet) {
        var player = packet.getPacketListener().getPlayer();
        var level = player.level();
        var nodePos = packet.nodePos;
        var newName = packet.newName;
        WirelessManager.setNodeName(player, level, nodePos, newName);
    }

    @SubscribePacket
    public static void onSetNodePass(SetNodePassPacket packet) {
        var player = packet.getPacketListener().getPlayer();
        var level = player.level();
        var nodePos = packet.nodePos;
        var newPass = packet.newPass;
        WirelessManager.setNodePass(player, level, nodePos, newPass);
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
            AcademyCraft.LOGGER.warn("Player {} tried to rename nonexistent node at {}",
                    player.getGameProfile().getName(), nodePos);
            return;
        }

        var newNodePos = data.findNodePositionByName(newName);
        if (newNodePos != null && !newNodePos.equals(nodePos)) {
            AcademyCraft.LOGGER.warn("Player {} tried to rename node at {} to '{}', but that name is already taken by node at {}",
                    player.getGameProfile().getName(), nodePos, newName, newNodePos);
            return;
        }

        var oldNameForMap = oldCfg.name;
        oldCfg.name = newName;
        data.nodeNameMap.remove(oldNameForMap);
        data.nodeNameMap.put(newName, nodePos);

        data.setDirty();
        AcademyCraft.LOGGER.debug("Player {} renamed node at {} from '{}' to '{}'",
                player.getGameProfile().getName(), nodePos, oldNameForMap, newName);
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
            AcademyCraft.LOGGER.warn("Player {} tried to change password of nonexistent node at {}",
                    player.getGameProfile().getName(), nodePos);
            return;
        }

        cfg.password = newPass;
        data.setDirty();
        AcademyCraft.LOGGER.debug("Player {} changed password of node '{}' at {}",
                player.getGameProfile().getName(), cfg.name, nodePos);
    }

    public static void handleConnect(ServerPlayer player, ServerLevel level, BlockPos userPos, String targetNodeName, String passwordAttempt) {
        var networkData = WirelessNetworkData.get(level);

        var nodePos = networkData.findNodePositionByName(targetNodeName);
        if (nodePos == null) {
            AcademyCraft.LOGGER.warn("Player {} failed connecting user at {}: Node '{}' not found.", player.getGameProfile().getName(), userPos, targetNodeName);
            return;
        }

        var nodeConfig = networkData.getNodeConfig(nodePos);

        if (nodeConfig == null) {
            AcademyCraft.LOGGER.error("Node position {} found for '{}' but NodeConfig is missing!", nodePos, targetNodeName);
            return;
        }

        if (nodeConfig.connectedUsers.size() >= nodeConfig.maxConnections) {
            AcademyCraft.LOGGER.warn("Node '{}' has reached its maximum connection limit. User at {} cannot connect.", targetNodeName, userPos);
            return;
        }

        var userBE = level.getBlockEntity(userPos);
        if (!(userBE instanceof WirelessUser wirelessUser)) {
            AcademyCraft.LOGGER.warn("Player {} tried to connect invalid block at {} to node '{}'. Block is not a WirelessUser.", player.getGameProfile().getName(), userPos, targetNodeName);
            return;
        }

        if (nodePos.distSqr(userPos) > (double) nodeConfig.radius * nodeConfig.radius) {
            AcademyCraft.LOGGER.warn("User at {} is too far from node '{}' (Radius: {}).", userPos, targetNodeName, nodeConfig.radius);
            return;
        }

        if (!nodeConfig.checkPassword(passwordAttempt)) {
            AcademyCraft.LOGGER.warn("Incorrect password provided by {} for node '{}' from user at {}.", player.getGameProfile().getName(), targetNodeName, userPos);
            return;
        }

        if (networkData.connectUserToNode(nodePos, userPos)) {
            wirelessUser.setConnectedNodePosition(nodePos);
            AcademyCraft.LOGGER.debug("User at {} successfully connected to node '{}' (at {}).", userPos, targetNodeName, nodePos);
        } else {
            AcademyCraft.LOGGER.warn("Failed connecting user {} to node '{}': Node likely full or user already connected elsewhere.", userPos, targetNodeName);
        }
    }

    public static void handleDisconnect(@Nullable ServerPlayer player, ServerLevel level, BlockPos userPos) {
        var userBE = level.getBlockEntity(userPos);
        if (!(userBE instanceof WirelessUser wirelessUser)) {
            var playerName = (player != null) ? player.getGameProfile().getName() : "System";
            AcademyCraft.LOGGER.warn("{} tried to disconnect invalid block at {}.", playerName, userPos);
            return;
        }

        var connectedNodePosition = wirelessUser.getConnectedNodePosition();

        if (connectedNodePosition != null) {
            var networkData = WirelessNetworkData.get(level);
            var removedFromData = networkData.disconnectUserFromNode(connectedNodePosition, userPos);
            if (removedFromData) {
                AcademyCraft.LOGGER.debug("Successfully removed user {} from node {}'s list in SavedData.", userPos, connectedNodePosition);
            } else {
                AcademyCraft.LOGGER.debug("Attempted to remove user {} from node {}'s list in SavedData, but the association was not found (possibly already removed or node unregistered).", userPos, connectedNodePosition);
            }
        } else {
            AcademyCraft.LOGGER.debug("User at {} was not connected to any node according to its own state.", userPos);
        }

        wirelessUser.setConnectedNodePosition(null);
        AcademyCraft.LOGGER.debug("User at {} connection state (WirelessUser instance) cleared.", userPos);
    }

    public static List<String> getAvailableNodes(ServerLevel level, BlockPos requesterPos) {
        var data = WirelessNetworkData.get(level);
        var nodeNamesInRange = new ArrayList<String>();
        for (var entry : data.getNodeEntries().entrySet()) {
            var nodePos = entry.getKey();
            var config = entry.getValue();
            if (nodePos.distSqr(requesterPos) <= (double) config.radius * config.radius) {
                nodeNamesInRange.add(config.name);
            }
        }
        return nodeNamesInRange;
    }

    public static Pair<Boolean,String> getCurrentNode(ServerLevel level, BlockPos userPos) {
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

        var transferRate = node.getEnergyTransferRate();
        var energyStored = node.getEnergyStored();
        var maxEnergy = node.getMaxEnergyStorage();

        var extractSources = new HashMap<WirelessUser, Integer>();
        var insertTargets = new HashMap<WirelessUser, Integer>();
        var extractWeight = 0.0;
        var insertWeight = 0.0;

        for (var entry : userConfigMap.entrySet()) {
            var user = entry.getKey();
            if (user == node) {
                continue;
            }
            var cfg = entry.getValue();
            var receiveWeight = cfg.getReceiveWeight();
            var sendWeight = cfg.getSendWeight();

            var canExtract = node.extractFromUser(user, transferRate, true);
            if (canExtract > 0) {
                extractSources.put(user, canExtract);
                extractWeight += receiveWeight;
            }

            var canInsert = node.insertIntoUser(user, transferRate, true);
            if (canInsert > 0) {
                insertTargets.put(user, canInsert);
                insertWeight += sendWeight;
            }
        }

        if (insertTargets.isEmpty() && extractSources.isEmpty()) return;

        var remainingBandwidth = transferRate;

        if (!insertTargets.isEmpty() && energyStored > 0) {
            for (var entry : insertTargets.entrySet()) {
                if (remainingBandwidth <= 0 || energyStored <= 0) break;
                var user = entry.getKey();
                var capacity = entry.getValue();
                var weight = userConfigMap.get(user).getSendWeight();

                var share = (insertWeight > 0) ? (int) Math.floor((weight / insertWeight) * transferRate) : 0;
                var amount = Math.min(Math.min(share, capacity), Math.min(energyStored, remainingBandwidth));
                if (amount <= 0) continue;

                var moved = node.insertIntoUser(user, amount, false);
                if (moved > 0) {
                    energyStored -= moved;
                    remainingBandwidth -= moved;
                }
            }
        }

        if (!extractSources.isEmpty() && remainingBandwidth > 0 && energyStored < maxEnergy) {
            for (var entry : extractSources.entrySet()) {
                if (remainingBandwidth <= 0 || energyStored >= maxEnergy) break;
                var user = entry.getKey();
                var capacity = entry.getValue();
                var weight = userConfigMap.get(user).getReceiveWeight();

                var share = (extractWeight > 0) ? (int) Math.floor((weight / extractWeight) * remainingBandwidth) : 0;
                var amount = Math.min(Math.min(share, capacity), Math.min(maxEnergy - energyStored, remainingBandwidth));
                if (amount <= 0) continue;

                var moved = node.extractFromUser(user, amount, false);
                if (moved > 0) {
                    energyStored += moved;
                    remainingBandwidth -= moved;
                }
            }
        }

        node.setEnergyStored(energyStored);
    }

    private WirelessManager() {
    }
}