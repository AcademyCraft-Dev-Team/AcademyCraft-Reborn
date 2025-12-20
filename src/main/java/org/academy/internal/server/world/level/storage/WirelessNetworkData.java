package org.academy.internal.server.world.level.storage;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Keyable;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.academy.api.server.wireless.WirelessManager;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

public final class WirelessNetworkData extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private static final Codec<BlockPos> BLOCKPOS_AS_STRING_CODEC = Codec.STRING.flatXmap(
            s -> {
                try {
                    var parts = s.split(",");
                    if (parts.length != 3) {
                        return DataResult.error(() -> "Invalid Block Pos string format: " + s);
                    }
                    var x = Integer.parseInt(parts[0].trim());
                    var y = Integer.parseInt(parts[1].trim());
                    var z = Integer.parseInt(parts[2].trim());
                    return DataResult.success(new BlockPos(x, y, z));
                } catch (NumberFormatException e) {
                    return DataResult.error(() -> "Failed to parse Block Pos from string: " + s);
                }
            },
            pos -> DataResult.success(pos.getX() + ", " + pos.getY() + ", " + pos.getZ())
    ).stable();

    public static final Codec<WirelessNetworkData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.simpleMap(BLOCKPOS_AS_STRING_CODEC, NodeConfig.CODEC, Keyable.forStrings(Stream::empty))
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
        initialNodes.forEach(nodes::put);
    }

    public static WirelessNetworkData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(SAVED_DATA_TYPE);
    }

    public boolean registerNode(BlockPos pos, String name, String password, int radius, int maxConnections) {
        if (nodes.containsPrimary(pos) || nodes.containsSecondary(name)) {
            LOGGER.warn("Register failed: Node at {} or name '{}' already exists.", pos, name);
            return false;
        }

        var config = new NodeConfig(name, password, radius, maxConnections, new HashMap<>());
        nodes.put(pos, config);
        setDirty();
        LOGGER.debug("Registered node '{}' at {}", name, pos);
        return true;
    }

    public void unregisterNode(BlockPos pos, ServerLevel level) {
        var config = nodes.removeByPrimary(pos);
        if (config == null) {
            LOGGER.warn("Attempted to unregister a node at {} but it was not found in configurations.", pos);
            return;
        }

        setDirty();
        LOGGER.debug("Unregistered node '{}' at {}. Now disconnecting its users.", config.name, pos);

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
            LOGGER.warn("Connect failed: Node at {} not found.", nodePos);
            return false;
        }

        if (config.connectedUsers.size() >= config.maxConnections) {
            LOGGER.warn("Node '{}' at {} is at full capacity.", config.name, nodePos);
            return false;
        }

        if (config.connectedUsers.containsKey(userPos)) {
            LOGGER.warn("User {} is already connected to node '{}'", userPos, config.name);
            return false;
        }

        config.connectedUsers.put(userPos, new UserConfig());
        LOGGER.debug("Connected user {} to node '{}'", userPos, config.name);
        setDirty();
        return true;
    }

    public boolean disconnectUserFromNode(BlockPos nodePos, BlockPos userPos) {
        var config = getNodeConfig(nodePos);
        if (config == null) {
            return false;
        }

        if (config.connectedUsers.remove(userPos) != null) {
            LOGGER.debug("Disconnected user {} from node '{}'", userPos, config.name);
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
        public final Map<BlockPos, UserConfig> connectedUsers;

        public static final Codec<NodeConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("name").forGetter(config -> config.name),
                Codec.STRING.fieldOf("password").forGetter(config -> config.password),
                Codec.INT.fieldOf("radius").forGetter(config -> config.radius),
                Codec.INT.fieldOf("max_connections").forGetter(config -> config.maxConnections),
                Codec.simpleMap(BLOCKPOS_AS_STRING_CODEC, UserConfig.CODEC, Keyable.forStrings(Stream::empty))
                        .fieldOf("users")
                        .forGetter(config -> config.connectedUsers)
        ).apply(instance, NodeConfig::new));

        public NodeConfig(String name, String password, int radius, int maxConnections, Map<BlockPos, UserConfig> connectedUsers) {
            this.name = name;
            this.password = password;
            this.radius = radius;
            this.maxConnections = maxConnections;
            this.connectedUsers = connectedUsers;
        }

        public boolean checkPassword(String attempt) {
            return password.equals(attempt);
        }
    }

    public record UserConfig(double receiveWeight, double sendWeight) {
        public static final Codec<UserConfig> CODEC = RecordCodecBuilder.create(
                instance -> instance.group(
                        Codec.DOUBLE.fieldOf("weight_receive")
                                .orElse(1.0)
                                .forGetter(UserConfig::receiveWeight),
                        Codec.DOUBLE.fieldOf("weight_send")
                                .orElse(1.0)
                                .forGetter(UserConfig::sendWeight)
                ).apply(instance, UserConfig::new)
        );

        public UserConfig() {
            this(1.0, 1.0);
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