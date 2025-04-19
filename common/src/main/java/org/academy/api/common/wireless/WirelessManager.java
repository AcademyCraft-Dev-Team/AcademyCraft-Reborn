package org.academy.api.common.wireless;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.academy.AcademyCraft;
import org.academy.api.client.network.NetworkSystemClient;
import org.academy.api.client.network.S2CPacketHandler;
import org.academy.api.common.network.Packets;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.common.network.packet.S2CPacket;
import org.academy.api.server.network.C2SPacketHandler;
import org.academy.api.server.network.NetworkSystemServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WirelessManager {
    public static final Map<BlockPos, WirelessNode> WIRELESS_NODES = new HashMap<>();

    public static void initClient() {
        NetworkSystemClient.registerS2CPacketHandler(Packets.S2C_SET_NODE, new S2CPacketHandler() {
            @Override
            public void handle(@NotNull ClientPacketListener listener, @NotNull S2CPacket packet) {
                BlockPos blockPos = packet.friendlyByteBuf.readBlockPos();
                String name = packet.friendlyByteBuf.readUtf();
                BlockEntity blockEntity = listener.getLevel().getBlockEntity(blockPos);
                WirelessNode wirelessNode = getWirelessNode(name);
                if (blockEntity instanceof WirelessUser wirelessUser) {
                    wirelessUser.setWirelessNode(wirelessNode);
                }
            }
        });
    }

    public static void initServer() {
        NetworkSystemServer.registerC2SPacketHandler(Packets.C2S_CONNECT_NODE, new C2SPacketHandler() {
            @Override
            public void handle(@NotNull ServerGamePacketListenerImpl listener, @NotNull C2SPacket packet) {
                BlockPos blockPos = packet.friendlyByteBuf.readBlockPos();
                String name = packet.friendlyByteBuf.readUtf();
                String password = packet.friendlyByteBuf.readUtf();
                WirelessNode wirelessNode = getWirelessNode(name);
                if (wirelessNode != null) {
                    BlockEntity blockEntity = listener.player.level().getBlockEntity(blockPos);
                    if (blockEntity instanceof WirelessUser wirelessUser) {
                        if (wirelessNode.getNodePassword().equals(password)) {
                            wirelessUser.setWirelessNode(wirelessNode);
                            listener.send(new S2CPacket(Packets.S2C_SET_NODE, blockPos, name));
                        }
                    }
                }
            }
        });
    }

    @Nullable
    public static WirelessNode getWirelessNode(String name) {
        for (WirelessNode node : WIRELESS_NODES.values()) {
            AcademyCraft.LOGGER.info("Checking node " + name);
            if (node.getNodeName().equals(name)) {
                return node;
            }
        }
        return null;
    }

    public static List<WirelessNode> getAvailableWirelessMasters(@NotNull BlockPos nodePos) {
        List<WirelessNode> result = new ArrayList<>();

        for (Map.Entry<BlockPos, WirelessNode> entry : WIRELESS_NODES.entrySet()) {
            BlockPos masterPos = entry.getKey();
            WirelessNode master = entry.getValue();

            int radius = master.getRadius();

            if (masterPos.distSqr(nodePos) <= radius * radius) {
                result.add(master);
            }
        }

        return result;
    }

    private WirelessManager() {
    }
}