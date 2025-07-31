package org.academy.internal.server.world.level.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.academy.AcademyCraft;
import org.academy.api.server.wireless.WirelessManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public class WirelessNetworkData extends SavedData {
    public final Map<BlockPos, NodeConfig> nodeConfigurations = new HashMap<>();
    public final Map<String, BlockPos> nodeNameMap = new HashMap<>();
    public static final String DATA_NAME = "wireless_network_data";

    public static WirelessNetworkData get(ServerLevel level) {
        var storage = level.getDataStorage();
        Supplier<WirelessNetworkData> creator = WirelessNetworkData::new;
        BiFunction<CompoundTag, HolderLookup.Provider, WirelessNetworkData> loader = WirelessNetworkData::load;
        return storage.computeIfAbsent(new Factory<>(creator, loader), DATA_NAME);
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
        if (config != null) {
            var usersToDisconnect = new HashSet<>(config.connectedUsers.keySet());

            var nodeName = config.name;
            nodeNameMap.remove(nodeName);
            nodeConfigurations.remove(pos);
            setDirty();

            AcademyCraft.LOGGER.debug("Unregistered node '{}' at {}. Now disconnecting its users.", nodeName, pos);

            for (var userPos : usersToDisconnect) {
                WirelessManager.handleDisconnect(null, level, userPos);
            }
        } else {
            AcademyCraft.LOGGER.warn("Attempted to unregister a node at {} but it was not found in configurations.", pos);
        }
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
        if (config != null) {
            if (config.connectedUsers.size() < config.maxConnections) {
                if (!config.connectedUsers.containsKey(userPos)) {
                    config.connectedUsers.put(userPos, new UserConfig());
                    AcademyCraft.LOGGER.debug("Connected user {} to node '{}'", userPos, config.name);
                    setDirty();
                    return true;
                } else {
                    AcademyCraft.LOGGER.warn("User {} is already connected to node '{}'", userPos, config.name);
                }
            } else {
                AcademyCraft.LOGGER.warn("Node '{}' at {} is at full capacity.", config.name, nodePos);
            }
        } else {
            AcademyCraft.LOGGER.warn("Connect failed: Node '{}' at {} not found.", "null", nodePos);
        }
        return false;
    }

    public boolean disconnectUserFromNode(BlockPos nodePos, BlockPos userPos) {
        var config = nodeConfigurations.get(nodePos);
        if (config != null) {
            config.connectedUsers.remove(userPos);
            AcademyCraft.LOGGER.debug("Disconnected user {} from node '{}'", userPos, config.name);
            setDirty();
            return true;
        }
        return false;
    }

    public static WirelessNetworkData load(CompoundTag tag, HolderLookup.Provider provider) {
        var data = new WirelessNetworkData();
        AcademyCraft.LOGGER.debug("Loading WirelessNetworkData...");
        if (tag.contains("nodes", Tag.TAG_LIST)) {
            var nodesTag = tag.getList("nodes", Tag.TAG_COMPOUND);
            AcademyCraft.LOGGER.debug("Found {} nodes in NBT", nodesTag.size());
            for (int i = 0; i < nodesTag.size(); ++i) {
                var nodeEntryTag = nodesTag.getCompound(i);
                if (nodeEntryTag.contains("pos", Tag.TAG_LONG)) {
                    var pos = BlockPos.of(nodeEntryTag.getLong("pos"));
                    if (nodeEntryTag.contains("config", Tag.TAG_COMPOUND)) {
                        var config = NodeConfig.load(nodeEntryTag.getCompound("config"));
                        if (config != null && config.name != null) {
                            data.nodeConfigurations.put(pos, config);
                            data.nodeNameMap.put(config.name, pos);
                            AcademyCraft.LOGGER.debug("Loaded node '{}' at {}", config.name, pos);
                        } else {
                            AcademyCraft.LOGGER.error("Invalid config at {} during load", pos);
                        }
                    } else {
                        AcademyCraft.LOGGER.error("Missing 'config' for node at {}", pos);
                    }
                } else {
                    AcademyCraft.LOGGER.error("Node entry missing 'pos'");
                }
            }
        } else {
            AcademyCraft.LOGGER.warn("No 'nodes' tag found in NBT.");
        }
        return data;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        var nodesTag = new ListTag();
        AcademyCraft.LOGGER.debug("Saving {} node configurations...", nodeConfigurations.size());
        for (var entry : nodeConfigurations.entrySet()) {
            var pos = entry.getKey();
            var config = entry.getValue();
            if (pos != null && config != null) {
                var nodeEntryTag = new CompoundTag();
                nodeEntryTag.putLong("pos", pos.asLong());
                nodeEntryTag.put("config", config.save(new CompoundTag()));
                nodesTag.add(nodeEntryTag);
                AcademyCraft.LOGGER.debug("Saved node '{}' at {}", config.name, pos);
            } else {
                AcademyCraft.LOGGER.warn("Skipped saving null entry at {}", pos);
            }
        }
        tag.put("nodes", nodesTag);
        return tag;
    }

    public static class NodeConfig {
        public String name;
        public String password;
        public final int radius;
        public final int maxConnections;
        public final Map<BlockPos, UserConfig> connectedUsers = new HashMap<>();

        public NodeConfig(String name, String password, int radius, int maxConnections) {
            this.name = name;
            this.password = password;
            this.radius = radius;
            this.maxConnections = maxConnections;
        }

        private NodeConfig(String name, int radius, int maxConnections) {
            this.name = name;
            this.radius = radius;
            this.maxConnections = maxConnections;
            this.password = "";
        }

        public boolean checkPassword(String attempt) {
            return this.password.equals(attempt);
        }

        public CompoundTag save(CompoundTag tag) {
            tag.putString("name", name);
            tag.putString("password", password);
            tag.putInt("radius", radius);
            tag.putInt("max_connections", maxConnections);

            var usersTag = new ListTag();
            for (var entry : this.connectedUsers.entrySet()) {
                var userTag = new CompoundTag();
                userTag.putLong("pos", entry.getKey().asLong());
                userTag.putDouble("weight_receive", entry.getValue().getReceiveWeight());
                userTag.putDouble("weight_send", entry.getValue().getSendWeight());
                usersTag.add(userTag);
            }
            tag.put("users", usersTag);

            return tag;
        }

        public static NodeConfig load(CompoundTag tag) {
            if (!tag.contains("name", Tag.TAG_STRING) ||
                    !tag.contains("password", Tag.TAG_STRING) ||
                    !tag.contains("radius", Tag.TAG_INT) ||
                    !tag.contains("max_connections", Tag.TAG_INT)) {
                AcademyCraft.LOGGER.error("NodeConfig missing required fields: {}", tag);
                return null;
            }

            var name = tag.getString("name");
            var password = tag.getString("password");
            int radius = tag.getInt("radius");
            int maxConnections = tag.getInt("max_connections");

            var loaded = new NodeConfig(name, radius, maxConnections);
            loaded.password = password;

            if (tag.contains("users", Tag.TAG_LIST)) {
                var usersTag = tag.getList("users", Tag.TAG_COMPOUND);
                for (var baseTag : usersTag) {
                    if (baseTag instanceof CompoundTag userTag) {
                        var pos = BlockPos.of(userTag.getLong("pos"));
                        var weightReceive = userTag.getDouble("weight_receive");
                        var weightSend = userTag.getDouble("weight_send");
                        loaded.connectedUsers.put(pos, new UserConfig(weightReceive, weightSend));
                        AcademyCraft.LOGGER.warn("User tag missing required fields: {}", userTag);
                    }
                }
            }

            AcademyCraft.LOGGER.debug("Loaded NodeConfig '{}'", name);
            return loaded;
        }
    }

    public static class UserConfig {
        private final double receiveWeight;
        private final double sendWeight;

        public UserConfig(double receiveWeight, double sendWeight) {
            this.receiveWeight = receiveWeight;
            this.sendWeight = sendWeight;
        }

        public UserConfig() {
            this(1, 1);
        }

        public double getReceiveWeight() {
            return receiveWeight;
        }

        public double getSendWeight() {
            return sendWeight;
        }
    }
}
