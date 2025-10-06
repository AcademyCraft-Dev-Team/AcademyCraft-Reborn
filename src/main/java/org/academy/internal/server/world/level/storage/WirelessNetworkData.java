package org.academy.internal.server.world.level.storage;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.academy.AcademyCraft;
import org.academy.api.server.wireless.WirelessManager;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class WirelessNetworkData extends SavedData {

    private static final Codec<Pair<BlockPos, NodeConfig>> NODE_ENTRY_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    BlockPos.CODEC.fieldOf("pos").forGetter(Pair::getFirst),
                    NodeConfig.CODEC.fieldOf("config").forGetter(Pair::getSecond)
            ).apply(instance, Pair::new));

    public static final Codec<WirelessNetworkData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.list(NODE_ENTRY_CODEC).fieldOf("nodes").forGetter(WirelessNetworkData::toNodeList)
            ).apply(instance, WirelessNetworkData::fromNodeList)
    );

    public static final SavedDataType<WirelessNetworkData> SAVED_DATA_TYPE = new SavedDataType<>(
            "academy_wireless",
            WirelessNetworkData::new,
            CODEC
    );

    public final Map<BlockPos, NodeConfig> nodeConfigurations = new HashMap<>();
    public final Map<String, BlockPos> nodeNameMap = new HashMap<>();

    public static WirelessNetworkData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(SAVED_DATA_TYPE);
    }

    private static WirelessNetworkData fromNodeList(List<Pair<BlockPos, NodeConfig>> nodes) {
        var data = new WirelessNetworkData();
        for (var pair : nodes) {
            var pos = pair.getFirst();
            var config = pair.getSecond();
            if (pos == null || config == null || config.name == null)
                continue;

            data.nodeConfigurations.put(pos, config);
            data.nodeNameMap.put(config.name, pos);
        }
        return data;
    }

    private List<Pair<BlockPos, NodeConfig>> toNodeList() {
        return this.nodeConfigurations.entrySet()
                .stream()
                .map(entry -> Pair.of(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    public boolean registerNode(BlockPos pos, String name, String password, int radius, int maxConnections) {
        if (nodeConfigurations.containsKey(pos) || nodeNameMap.containsKey(name)) {
            AcademyCraft.LOGGER.warn("Register failed: Node at {} or name '{}' already exists.", pos, name);
            return false;
        }

        var config = new NodeConfig(name, password, radius, maxConnections);
        nodeConfigurations.put(pos, config);
        nodeNameMap.put(name, pos);
        setDirty();
        AcademyCraft.LOGGER.debug("Registered node '{}' at {}", name, pos);
        return true;
    }

    public void unregisterNode(BlockPos pos, ServerLevel level) {
        var config = nodeConfigurations.get(pos);
        if (config == null) {
            AcademyCraft.LOGGER.warn("Attempted to unregister a node at {} but it was not found in configurations.", pos);
            return;
        }

        var usersToDisconnect = new HashSet<>(config.connectedUsers.keySet());
        var nodeName = config.name;

        nodeNameMap.remove(nodeName);
        nodeConfigurations.remove(pos);
        setDirty();

        AcademyCraft.LOGGER.debug("Unregistered node '{}' at {}. Now disconnecting its users.", nodeName, pos);

        for (var userPos : usersToDisconnect)
            WirelessManager.handleDisconnect(null, level, userPos);
    }

    @Nullable
    public NodeConfig getNodeConfig(BlockPos pos) {
        return nodeConfigurations.get(pos);
    }

    @Nullable
    public BlockPos findNodePositionByName(String name) {
        return nodeNameMap.get(name);
    }

    public Map<BlockPos, NodeConfig> getNodeEntries() {
        return Collections.unmodifiableMap(this.nodeConfigurations);
    }

    public boolean connectUserToNode(BlockPos nodePos, BlockPos userPos) {
        var config = nodeConfigurations.get(nodePos);
        if (config == null) {
            AcademyCraft.LOGGER.warn("Connect failed: Node at {} not found.", nodePos);
            return false;
        }

        if (config.connectedUsers.size() >= config.maxConnections) {
            AcademyCraft.LOGGER.warn("Node '{}' at {} is at full capacity.", config.name, nodePos);
            return false;
        }

        if (config.connectedUsers.containsKey(userPos)) {
            AcademyCraft.LOGGER.warn("User {} is already connected to node '{}'", userPos, config.name);
            return false;
        }

        config.connectedUsers.put(userPos, new UserConfig());
        AcademyCraft.LOGGER.debug("Connected user {} to node '{}'", userPos, config.name);
        setDirty();
        return true;
    }

    public boolean disconnectUserFromNode(BlockPos nodePos, BlockPos userPos) {
        var config = nodeConfigurations.get(nodePos);
        if (config == null)
            return false;

        config.connectedUsers.remove(userPos);
        AcademyCraft.LOGGER.debug("Disconnected user {} from node '{}'", userPos, config.name);
        setDirty();
        return true;
    }

    public static class NodeConfig {
        public String name;
        public String password;
        public final int radius;
        public final int maxConnections;
        public final Map<BlockPos, UserConfig> connectedUsers = new HashMap<>();

        public static final Codec<NodeConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("name").forGetter(config -> config.name),
                Codec.STRING.fieldOf("password").forGetter(config -> config.password),
                Codec.INT.fieldOf("radius").forGetter(config -> config.radius),
                Codec.INT.fieldOf("max_connections").forGetter(config -> config.maxConnections),
                Codec.unboundedMap(BlockPos.CODEC, UserConfig.CODEC).fieldOf("users").forGetter(config -> config.connectedUsers)
        ).apply(instance, (name, password, radius, maxConnections, users) -> {
            var config = new NodeConfig(name, password, radius, maxConnections);
            config.connectedUsers.putAll(users);
            return config;
        }));

        public NodeConfig(String name, String password, int radius, int maxConnections) {
            this.name = name;
            this.password = password;
            this.radius = radius;
            this.maxConnections = maxConnections;
        }

        public boolean checkPassword(String attempt) {
            return this.password.equals(attempt);
        }
    }

    public static class UserConfig {
        private final double receiveWeight;
        private final double sendWeight;

        public static final Codec<UserConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.DOUBLE.fieldOf("weight_receive").orElse(1.0).forGetter(UserConfig::getReceiveWeight),
                Codec.DOUBLE.fieldOf("weight_send").orElse(1.0).forGetter(UserConfig::getSendWeight)
        ).apply(instance, UserConfig::new));

        public UserConfig(double receiveWeight, double sendWeight) {
            this.receiveWeight = receiveWeight;
            this.sendWeight = sendWeight;
        }

        public UserConfig() {
            this(1.0, 1.0);
        }

        public double getReceiveWeight() {
            return receiveWeight;
        }

        public double getSendWeight() {
            return sendWeight;
        }
    }
}