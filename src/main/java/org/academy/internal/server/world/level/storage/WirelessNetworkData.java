package org.academy.internal.server.world.level.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.academy.AcademyCraft;
import org.academy.api.server.wireless.WirelessManager;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Function;

public final class WirelessNetworkData extends SavedData {
    private static final Codec<BlockPos> BLOCKPOS_AS_STRING_CODEC = Codec.STRING.flatXmap(
            s -> {
                try {
                    var parts = s.split(",");
                    if (parts.length != 3) {
                        return DataResult.error(() -> "Invalid BlockPos string format: " + s);
                    }
                    var x = Integer.parseInt(parts[0].trim());
                    var y = Integer.parseInt(parts[1].trim());
                    var z = Integer.parseInt(parts[2].trim());
                    return DataResult.success(new BlockPos(x, y, z));
                } catch (NumberFormatException e) {
                    return DataResult.error(() -> "Failed to parse BlockPos from string: " + s);
                }
            },
            pos -> DataResult.success(pos.getX() + ", " + pos.getY() + ", " + pos.getZ())
    ).stable();

    public static final Codec<WirelessNetworkData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.unboundedMap(BLOCKPOS_AS_STRING_CODEC, NodeConfig.CODEC)
                            .fieldOf("nodes")
                            .forGetter(data -> data.nodes.getPrimaryMap())
            ).apply(instance, WirelessNetworkData::new)
    );

    public static final SavedDataType<WirelessNetworkData> SAVED_DATA_TYPE = new SavedDataType<>(
            "academy_wireless",
            WirelessNetworkData::new,
            CODEC
    );

    private final DualKeyMap<BlockPos, String, NodeConfig> nodes =
            new DualKeyMap<>(nodeConfig -> nodeConfig.name);

    public WirelessNetworkData() {}

    private WirelessNetworkData(Map<BlockPos, NodeConfig> initialNodes) {
        initialNodes.forEach(this.nodes::put);
    }

    public static WirelessNetworkData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(SAVED_DATA_TYPE);
    }

    public boolean registerNode(BlockPos pos, String name, String password, int radius, int maxConnections) {
        if (nodes.containsPrimary(pos) || nodes.containsSecondary(name)) {
            AcademyCraft.LOGGER.warn("Register failed: Node at {} or name '{}' already exists.", pos, name);
            return false;
        }

        var config = new NodeConfig(name, password, radius, maxConnections);
        nodes.put(pos, config);
        setDirty();
        AcademyCraft.LOGGER.debug("Registered node '{}' at {}", name, pos);
        return true;
    }

    public void unregisterNode(BlockPos pos, ServerLevel level) {
        var config = nodes.removeByPrimary(pos);
        if (config == null) {
            AcademyCraft.LOGGER.warn("Attempted to unregister a node at {} but it was not found in configurations.", pos);
            return;
        }

        setDirty();
        AcademyCraft.LOGGER.debug("Unregistered node '{}' at {}. Now disconnecting its users.", config.name, pos);

        var usersToDisconnect = new HashSet<>(config.connectedUsers.keySet());
        for (var userPos : usersToDisconnect) {
            WirelessManager.handleDisconnect(null, level, userPos);
        }
    }

    public boolean renameNode(BlockPos pos, String newName) {
        var existingPos = nodes.getPrimaryBySecondary(newName);
        if (existingPos != null && !existingPos.equals(pos)) {
            return false;
        }

        var config = nodes.getByPrimary(pos);
        if (config == null) {
            return false;
        }

        nodes.removeByPrimary(pos);
        config.name = newName;
        nodes.put(pos, config);
        setDirty();
        return true;
    }

    @Nullable
    public NodeConfig getNodeConfig(BlockPos pos) {
        return nodes.getByPrimary(pos);
    }

    @Nullable
    public NodeConfig getNodeConfigByName(String name) {
        return nodes.getBySecondary(name);
    }

    @Nullable
    public BlockPos findNodePositionByName(String name) {
        return nodes.getPrimaryBySecondary(name);
    }

    public Map<BlockPos, NodeConfig> getAllNodes() {
        return nodes.getPrimaryMap();
    }

    public boolean connectUserToNode(BlockPos nodePos, BlockPos userPos) {
        var config = getNodeConfig(nodePos);
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
        var config = getNodeConfig(nodePos);
        if (config == null) {
            return false;
        }

        if (config.connectedUsers.remove(userPos) != null) {
            AcademyCraft.LOGGER.debug("Disconnected user {} from node '{}'", userPos, config.name);
            setDirty();
            return true;
        }
        return false;
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
                Codec.unboundedMap(BLOCKPOS_AS_STRING_CODEC, UserConfig.CODEC)
                        .fieldOf("users")
                        .forGetter(config -> config.connectedUsers)
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

    private static class DualKeyMap<K1, K2, V> {
        private final Map<K1, V> primaryMap = new HashMap<>();
        private final Map<K2, K1> secondaryIndex = new HashMap<>();
        private final Function<V, K2> secondaryKeyExtractor;

        public DualKeyMap(Function<V, K2> secondaryKeyExtractor) {
            this.secondaryKeyExtractor = secondaryKeyExtractor;
        }

        public void put(K1 key1, V value) {
            var key2 = secondaryKeyExtractor.apply(value);
            if (secondaryIndex.containsKey(key2) && !secondaryIndex.get(key2).equals(key1)) {
                throw new IllegalArgumentException("Secondary key '" + key2 + "' is already associated with a different primary key.");
            }

            var oldValue = primaryMap.get(key1);
            if (oldValue != null) {
                secondaryIndex.remove(secondaryKeyExtractor.apply(oldValue));
            }

            primaryMap.put(key1, value);
            secondaryIndex.put(key2, key1);
        }

        @Nullable
        public V getByPrimary(K1 key1) {
            return primaryMap.get(key1);
        }

        @Nullable
        public V getBySecondary(K2 key2) {
            var key1 = secondaryIndex.get(key2);
            if (key1 == null) {
                return null;
            }
            return primaryMap.get(key1);
        }

        @Nullable
        public K1 getPrimaryBySecondary(K2 key2) {
            return secondaryIndex.get(key2);
        }

        @Nullable
        public V removeByPrimary(K1 key1) {
            var removedValue = primaryMap.remove(key1);
            if (removedValue != null) {
                secondaryIndex.remove(secondaryKeyExtractor.apply(removedValue));
            }
            return removedValue;
        }

        public boolean containsPrimary(K1 key1) {
            return primaryMap.containsKey(key1);
        }

        public boolean containsSecondary(K2 key2) {
            return secondaryIndex.containsKey(key2);
        }

        public Map<K1, V> getPrimaryMap() {
            return Collections.unmodifiableMap(primaryMap);
        }
    }
}