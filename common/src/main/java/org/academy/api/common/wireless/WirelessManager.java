package org.academy.api.common.wireless;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.academy.AcademyCraft;
import org.academy.api.common.network.Packets;
import org.academy.api.server.network.NetworkSystemServer;
import org.academy.internal.server.world.level.storage.WorldData;

import java.util.Objects;

public class WirelessManager {
    public static void initClient() {
    }

    public static void initServer() {
        NetworkSystemServer.registerC2SPacketHandler(Packets.C2S_CONNECT_NODE, (listener, packet) -> {
            ServerPlayer player = listener.player;
            ServerLevel level = player.serverLevel();

            FriendlyByteBuf buf = packet.friendlyByteBuf;
            BlockPos userPos = buf.readBlockPos();
            String targetNodeName = buf.readUtf();
            String passwordAttempt = buf.readUtf();

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
        });

        NetworkSystemServer.registerC2SPacketHandler(Packets.C2S_DISCONNECT_NODE, (listener, packet) -> {
            ServerPlayer player = listener.player;
            ServerLevel level = player.serverLevel();

            try {
                BlockPos userPos = packet.friendlyByteBuf.readBlockPos();

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

            } catch (Exception e) {
                AcademyCraft.LOGGER.error("Error handling C2S_DISCONNECT_NODE packet: {}", e.getMessage(), e);
            }
        });
    }

    private WirelessManager() {
    }
}