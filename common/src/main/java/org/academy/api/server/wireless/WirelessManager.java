package org.academy.api.server.wireless;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.academy.AcademyCraft;
import org.academy.api.common.network.Packets;
import org.academy.api.common.wireless.WirelessNode;
import org.academy.api.common.wireless.WirelessUser;
import org.academy.api.server.network.FutureManagerServer;
import org.academy.api.server.network.NetworkSystemServer;
import org.academy.internal.server.world.level.storage.WorldData;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

public class WirelessManager {
    public static void initServer() {
        NetworkSystemServer.registerC2SPacketHandler(Packets.C2S_CONNECT_NODE, (listener, packet) -> {
            ServerPlayer player = listener.player;
            ServerLevel level = player.serverLevel();
            FriendlyByteBuf buf = packet.friendlyByteBuf;
            BlockPos userPos = buf.readBlockPos();
            String targetNodeName = buf.readUtf();
            String passwordAttempt = buf.readUtf();
            handleConnect(player, level, userPos, targetNodeName, passwordAttempt);
        });

        NetworkSystemServer.registerC2SPacketHandler(Packets.C2S_DISCONNECT_NODE, (listener, packet) -> {
            ServerPlayer player = listener.player;
            ServerLevel level = player.serverLevel();
            BlockPos userPos = packet.friendlyByteBuf.readBlockPos();
            handleDisconnect(player, level, userPos);
        });

        NetworkSystemServer.registerC2SPacketHandler(Packets.C2S_GET_AVAILABLE_NODES,
                (listener, packet) -> {
                    ServerLevel level = listener.player.serverLevel();
                    int id = packet.friendlyByteBuf.readVarInt();
                    BlockPos requesterPos = packet.friendlyByteBuf.readBlockPos();
                    FutureManagerServer.sendResult(listener, id, getAvailableNodes(level, requesterPos));
                }
        );

        NetworkSystemServer.registerC2SPacketHandler(Packets.C2S_GET_CURRENT_NODE, (listener, packet) -> {
            ServerLevel level = listener.player.serverLevel();
            int id = packet.friendlyByteBuf.readVarInt();
            BlockPos userPos = packet.friendlyByteBuf.readBlockPos();
            Pair<Boolean, String> currentNode = getCurrentNode(level, userPos);
            boolean isNull = currentNode.getLeft();
            FutureManagerServer.sendResult(listener, id, Pair.of(isNull, currentNode.getRight()));
        });

        NetworkSystemServer.registerC2SPacketHandler(Packets.C2S_SET_NODE_NAME, (listener, packet) -> {
            ServerPlayer player = listener.player;
            ServerLevel level = player.serverLevel();
            BlockPos nodePos = packet.friendlyByteBuf.readBlockPos();
            String newName = packet.friendlyByteBuf.readUtf();
            WirelessManager.setNodeName(player, level, nodePos, newName);
        });

        NetworkSystemServer.registerC2SPacketHandler(Packets.C2S_SET_NODE_PASS, (listener, packet) -> {
            ServerPlayer player = listener.player;
            ServerLevel level = player.serverLevel();
            BlockPos nodePos = packet.friendlyByteBuf.readBlockPos();
            String newPass = packet.friendlyByteBuf.readUtf();
            WirelessManager.setNodePass(player, level, nodePos, newPass);
        });
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

        if (data.findNodePositionByName(newName) != null) {
            AcademyCraft.LOGGER.warn("Player {} tried to rename node at {} to '{}', but that name is already taken",
                    player.getGameProfile().getName(), nodePos, newName);
            return;
        }

        data.nodeNameMap.remove(oldCfg.name);
        oldCfg.name = newName;
        data.nodeNameMap.put(newName, nodePos);

        data.setDirty();
        AcademyCraft.LOGGER.debug("Player {} renamed node at {} from '{}' to '{}'",
                player.getGameProfile().getName(), nodePos, oldCfg.name, newName);
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
            AcademyCraft.LOGGER.warn("Failed connecting user {} to node '{}': Node likely full.", userPos, targetNodeName);
        }
    }

    public static void handleDisconnect(ServerPlayer player, ServerLevel level, BlockPos userPos) {
        BlockEntity userBE = level.getBlockEntity(userPos);
        if (!(userBE instanceof WirelessUser wirelessUser)) {
            AcademyCraft.LOGGER.warn("Player {} tried to disconnect invalid block at {}.", player.getGameProfile().getName(), userPos);
            return;
        }

        BlockPos connectedNodePos = wirelessUser.getConnectedNodePosition();
        if (connectedNodePos == null) {
            AcademyCraft.LOGGER.debug("User at {} tried to disconnect but wasn't connected.", userPos);
            return;
        }

        WorldData.WirelessNetworkData networkData = WorldData.WirelessNetworkData.get(level);

        if (networkData.disconnectUserFromNode(connectedNodePos, userPos)) {
            wirelessUser.setConnectedNodePosition(null);
            AcademyCraft.LOGGER.debug("User at {} successfully disconnected from node at {}.", userPos, connectedNodePos);
        } else {
            AcademyCraft.LOGGER.warn("Failed request to disconnect user {} from node {}.", userPos, connectedNodePos);
        }
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

                int share = (int) Math.floor((weight / insertWeight) * transferRate);
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

                int share = (int) Math.floor((weight / extractWeight) * remainingBandwidth);
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