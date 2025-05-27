package org.academy.api.server.wireless;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftServer;
import org.academy.api.common.network.SubscribePacket;
import org.academy.api.common.network.future.HandlePayload;
import org.academy.api.common.wireless.*;
import org.academy.internal.server.world.level.storage.WorldData;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;

public class WirelessManager {
    public static void initServer() {
        AcademyCraftServer.NETWORK_SYSTEM_SERVER_INSTANCE.registerPacketListener(WirelessManager.class);
        AcademyCraftServer.FUTURE_MANAGER_SERVER_INSTANCE.registerPayloadHandler(WirelessManager.class);
    }

    @HandlePayload
    public static GetAvailableNodesPacket.Response handleGetAvailableNodes(GetAvailableNodesPacket payload) {
        ServerPlayer player = null;
        Supplier<ServerGamePacketListenerImpl> supplier = payload.packetListenerSupplier;
        if (supplier != null) {
            player = supplier.get().player;
        }
        if (player == null) {
            AcademyCraft.LOGGER.error("WirelessManager: Player context not found for GetAvailableNodesPacket.");
            return new GetAvailableNodesPacket.Response(Collections.emptyList());
        }
        ServerLevel level = player.serverLevel();
        BlockPos requesterPos = payload.requesterPos;
        return new GetAvailableNodesPacket.Response(getAvailableNodes(level, requesterPos));
    }

    @HandlePayload
    public static GetCurrentNodePacket.Response handleGetCurrentNode(GetCurrentNodePacket payload) {
        ServerPlayer player = null;
        Supplier<ServerGamePacketListenerImpl> supplier = payload.packetListenerSupplier;
        if (supplier != null) {
            player = supplier.get().player;
        }
        if (player == null) {
            AcademyCraft.LOGGER.error("WirelessManager: Player context not found for GetCurrentNodePacket.");
            return new GetCurrentNodePacket.Response(true, "Error");
        }
        ServerLevel level = player.serverLevel();
        BlockPos userPos = payload.userPos;
        Pair<Boolean, String> currentNode = getCurrentNode(level, userPos);
        return new GetCurrentNodePacket.Response(currentNode.getLeft(), currentNode.getRight());
    }

    @SubscribePacket
    public static void handleConnectNode(ConnectNodePacket packet) {
        ServerPlayer player = packet.packetListenerSupplier.get().getPlayer();
        ServerLevel level = player.serverLevel();
        BlockPos userPos = packet.userPos;
        String targetNodeName = packet.targetNodeName;
        String passwordAttempt = packet.passwordAttempt;
        handleConnect(player, level, userPos, targetNodeName, passwordAttempt);
    }

    @SubscribePacket
    public static void handleDisconnectNode(DisconnectNodePacket packet) {
        ServerPlayer player = packet.packetListenerSupplier.get().getPlayer();
        ServerLevel level = player.serverLevel();
        BlockPos userPos = packet.userPos;
        handleDisconnect(player, level, userPos);
    }

    @SubscribePacket
    public static void handleSetNodeName(SetNodeNamePacket packet) {
        ServerPlayer player = packet.packetListenerSupplier.get().getPlayer();
        ServerLevel level = player.serverLevel();
        BlockPos nodePos = packet.nodePos;
        String newName = packet.newName;
        WirelessManager.setNodeName(player, level, nodePos, newName);
    }

    @SubscribePacket
    public static void handleSetNodePass(SetNodePassPacket packet) {
        ServerPlayer player = packet.packetListenerSupplier.get().getPlayer();
        ServerLevel level = player.serverLevel();
        BlockPos nodePos = packet.nodePos;
        String newPass = packet.newPass;
        WirelessManager.setNodePass(player, level, nodePos, newPass);
    }

    public static void setNodeName(ServerPlayer player,
                                   ServerLevel level,
                                   BlockPos nodePos,
                                   String newName) {
        WorldData.WirelessNetworkData data = WorldData.WirelessNetworkData.get(level);

        WorldData.WirelessNetworkData.NodeConfig oldCfg = data.getNodeConfig(nodePos);
        if (oldCfg == null) {
            AcademyCraft.LOGGER.warn("Player {} tried to rename nonexistent node at {}",
                    player.getGameProfile().getName(), nodePos);
            return;
        }

        BlockPos newNodePos = data.findNodePositionByName(newName);
        if (newNodePos != null && !newNodePos.equals(nodePos)) {
            AcademyCraft.LOGGER.warn("Player {} tried to rename node at {} to '{}', but that name is already taken by node at {}",
                    player.getGameProfile().getName(), nodePos, newName, newNodePos);
            return;
        }

        String oldNameForMap = oldCfg.name;
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
        WorldData.WirelessNetworkData data = WorldData.WirelessNetworkData.get(level);

        WorldData.WirelessNetworkData.NodeConfig cfg = data.getNodeConfig(nodePos);
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
        WorldData.WirelessNetworkData networkData = WorldData.WirelessNetworkData.get(level);

        BlockPos nodePos = networkData.findNodePositionByName(targetNodeName);
        if (nodePos == null) {
            AcademyCraft.LOGGER.warn("Player {} failed connecting user at {}: Node '{}' not found.", player.getGameProfile().getName(), userPos, targetNodeName);
            return;
        }

        WorldData.WirelessNetworkData.NodeConfig nodeConfig = networkData.getNodeConfig(nodePos);

        if (nodeConfig == null) {
            AcademyCraft.LOGGER.error("Node position {} found for '{}' but NodeConfig is missing!", nodePos, targetNodeName);
            return;
        }

        if (nodeConfig.connectedUsers.size() >= nodeConfig.maxConnections) {
            AcademyCraft.LOGGER.warn("Node '{}' has reached its maximum connection limit. User at {} cannot connect.", targetNodeName, userPos);
            return;
        }

        BlockEntity userBE = level.getBlockEntity(userPos);
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
        BlockEntity userBE = level.getBlockEntity(userPos);
        if (!(userBE instanceof WirelessUser wirelessUser)) {
            String playerName = (player != null) ? player.getGameProfile().getName() : "System";
            AcademyCraft.LOGGER.warn("{} tried to disconnect invalid block at {}.", playerName, userPos);
            return;
        }

        BlockPos connectedNodePosition = wirelessUser.getConnectedNodePosition();

        if (connectedNodePosition != null) {
            WorldData.WirelessNetworkData networkData = WorldData.WirelessNetworkData.get(level);
            boolean removedFromData = networkData.disconnectUserFromNode(connectedNodePosition, userPos);
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
        WorldData.WirelessNetworkData data = WorldData.WirelessNetworkData.get(level);
        List<String> nodeNamesInRange = new ArrayList<>();
        for (Map.Entry<BlockPos, WorldData.WirelessNetworkData.NodeConfig> entry : data.getNodeEntries().entrySet()) {
            BlockPos nodePos = entry.getKey();
            WorldData.WirelessNetworkData.NodeConfig config = entry.getValue();
            if (nodePos.distSqr(requesterPos) <= (double) config.radius * config.radius) {
                nodeNamesInRange.add(config.name);
            }
        }
        return nodeNamesInRange;
    }

    public static Pair<Boolean,String> getCurrentNode(ServerLevel level, BlockPos userPos) {
        String currentNodeName = null;
        BlockEntity be = level.getBlockEntity(userPos);
        if (be instanceof WirelessUser user) {
            BlockPos connectedNodePos = user.getConnectedNodePosition();
            if (connectedNodePos != null) {
                WorldData.WirelessNetworkData data = WorldData.WirelessNetworkData.get(level);
                WorldData.WirelessNetworkData.NodeConfig nodeConfig = data.getNodeConfig(connectedNodePos);
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
            Map<WirelessUser, WorldData.WirelessNetworkData.UserConfig> userConfigMap
    ) {
        if (userConfigMap.isEmpty()) return;

        int transferRate = node.getEnergyTransferRate();
        int energyStored = node.getEnergyStored();
        int maxEnergy = node.getMaxEnergyStorage();

        Map<WirelessUser, Integer> extractSources = new HashMap<>();
        Map<WirelessUser, Integer> insertTargets = new HashMap<>();
        double extractWeight = 0, insertWeight = 0;

        for (Map.Entry<WirelessUser, WorldData.WirelessNetworkData.UserConfig> entry : userConfigMap.entrySet()) {
            WirelessUser user = entry.getKey();
            if (user == node) {
                continue;
            }
            WorldData.WirelessNetworkData.UserConfig cfg = entry.getValue();
            double receiveWeight = cfg.getReceiveWeight();
            double sendWeight = cfg.getSendWeight();

            int canExtract = node.extractFromUser(user, transferRate, true);
            if (canExtract > 0) {
                extractSources.put(user, canExtract);
                extractWeight += receiveWeight;
            }

            int canInsert = node.insertIntoUser(user, transferRate, true);
            if (canInsert > 0) {
                insertTargets.put(user, canInsert);
                insertWeight += sendWeight;
            }
        }

        if (insertTargets.isEmpty() && extractSources.isEmpty()) return;

        int remainingBandwidth = transferRate;

        if (!insertTargets.isEmpty() && energyStored > 0) {
            for (Map.Entry<WirelessUser, Integer> entry : insertTargets.entrySet()) {
                if (remainingBandwidth <= 0 || energyStored <= 0) break;
                WirelessUser user = entry.getKey();
                int capacity = entry.getValue();
                double weight = userConfigMap.get(user).getSendWeight();

                int share = (insertWeight > 0) ? (int) Math.floor((weight / insertWeight) * transferRate) : 0;
                int amount = Math.min(Math.min(share, capacity), Math.min(energyStored, remainingBandwidth));
                if (amount <= 0) continue;

                int moved = node.insertIntoUser(user, amount, false);
                if (moved > 0) {
                    energyStored -= moved;
                    remainingBandwidth -= moved;
                }
            }
        }

        if (!extractSources.isEmpty() && remainingBandwidth > 0 && energyStored < maxEnergy) {
            for (Map.Entry<WirelessUser, Integer> entry : extractSources.entrySet()) {
                if (remainingBandwidth <= 0 || energyStored >= maxEnergy) break;
                WirelessUser user = entry.getKey();
                int capacity = entry.getValue();
                double weight = userConfigMap.get(user).getReceiveWeight();

                int share = (extractWeight > 0) ? (int) Math.floor((weight / extractWeight) * remainingBandwidth) : 0;
                int amount = Math.min(Math.min(share, capacity), Math.min(maxEnergy - energyStored, remainingBandwidth));
                if (amount <= 0) continue;

                int moved = node.extractFromUser(user, amount, false);
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