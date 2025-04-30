package org.academy.internal.server.world.level.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftServer;
import org.academy.api.common.util.GsonUtil;
import org.academy.api.server.ability.AbilitySystemServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

public class WorldData {
    @SerializedName("players")
    private final Map<UUID, Player> players = new HashMap<>();

    private static boolean isValidFile(File file) {
        final Gson gson = new GsonBuilder().create();

        try (FileReader fileReader = new FileReader(file)) {
            final JsonObject jsonObject;

            try {
                jsonObject = gson.fromJson(fileReader, JsonObject.class);
            } catch (JsonSyntaxException e) {
                return false;
            }

            if (jsonObject == null) {
                return false;
            }

            Field[] fields = WorldData.class.getDeclaredFields();

            return GsonUtil.isValidField(jsonObject, fields);
        } catch (IOException e) {
            return false;
        }
    }

    public static WorldData getWorldData(File file) {
        final Gson gson = new GsonBuilder().setPrettyPrinting().create();
        if (!isValidFile(file)) {
            WorldData worldData = new WorldData();
            AcademyCraft.LOGGER.debug("Creating new world data file.");
            try (FileWriter fileWriter = new FileWriter(file)) {
                gson.toJson(worldData, fileWriter);
            } catch (IOException e) {
                throw new RuntimeException("Failed to create world data file", e);
            }
            return worldData;
        }

        try (FileReader reader = new FileReader(file)) {
            return gson.fromJson(reader, WorldData.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read world data file", e);
        }
    }

    public static void saveData() {
        if (AcademyCraftServer.worldData == null) {
            return;
        }
        final Gson gson = new GsonBuilder().setPrettyPrinting().create();
        final File configFile = AcademyCraftServer.worldDataFile;

        try (FileWriter fileWriter = new FileWriter(configFile)) {
            gson.toJson(AcademyCraftServer.worldData, fileWriter);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save world data", e);
        }
    }

    public Map<UUID, Player> getPlayers() {
        return players;
    }

    public static class Player {
        @SerializedName("skills")
        private final Set<String> skills = new HashSet<>();
        @SerializedName("skillData")
        private final Map<String, SkillData> skillData = new HashMap<>();
        @SerializedName("abilityCategory")
        private String abilityCategory;
        @SerializedName("level")
        private volatile int level;
        @SerializedName("computingPower")
        private volatile float computingPower = 0f;
        @SerializedName("maxComputingPower")
        private volatile float maxComputingPower = 100f;
        @SerializedName("computingPowerRecoverySpeed")
        private volatile float computingPowerRecoverySpeed = 1f;

        public final String getAbilityCategory() {
            return abilityCategory;
        }

        public final void setAbilityCategory(String abilityCategory) {
            this.abilityCategory = abilityCategory;
        }

        public final Set<String> getSkills() {
            return skills;
        }

        public final Map<String, SkillData> getSkillData() {
            return skillData;
        }

        public final int getLevel() {
            return level;
        }

        public final void setLevel(int level) {
            this.level = level;
        }

        public final float getComputingPower() {
            return computingPower;
        }

        public final void setComputingPower(float computingPower) {
            if (Float.isNaN(computingPower) || Float.isInfinite(computingPower)) {
                AbilitySystemServer.addTask(() -> this.computingPower = Math.min(getMaxComputingPower(), 0));
                return;
            }
            AbilitySystemServer.addTask(() -> this.computingPower = Math.min(getMaxComputingPower(), computingPower));
        }

        public float getMaxComputingPower() {
            return maxComputingPower;
        }

        public void setMaxComputingPower(float maxComputingPower) {
            AbilitySystemServer.addTask(() -> this.maxComputingPower = maxComputingPower);
        }

        public float getComputingPowerRecoverySpeed() {
            return computingPowerRecoverySpeed;
        }

        public void setComputingPowerRecoverySpeed(float computingPowerRecoverySpeed) {
            AbilitySystemServer.addTask(() -> this.computingPowerRecoverySpeed = computingPowerRecoverySpeed);
        }

        public static abstract class SkillData {
        }
    }

    public static class WirelessNetworkData extends SavedData {
        private final Map<BlockPos, NodeConfig> nodeConfigurations = new HashMap<>();
        private final Map<String, BlockPos> nodeNameMap = new HashMap<>();
        public static final String DATA_NAME = "wireless_network_data";

        public static WirelessNetworkData get(ServerLevel level) {
            DimensionDataStorage storage = level.getDataStorage();
            Supplier<WirelessNetworkData> creator = WirelessNetworkData::new;
            Function<CompoundTag, WirelessNetworkData> loader = WirelessNetworkData::load;
            return storage.computeIfAbsent(loader, creator, DATA_NAME);
        }

        public boolean registerNode(BlockPos pos, String name, String password, int radius, int maxConnections) {
            if (nodeConfigurations.containsKey(pos) || nodeNameMap.containsKey(name)) {
                AcademyCraft.LOGGER.warn("Register failed: Node at {} or name '{}' already exists.", pos, name);
                return false;
            }
            NodeConfig config = new NodeConfig(name, password, radius, maxConnections);
            nodeConfigurations.put(pos, config);
            nodeNameMap.put(name, pos);
            setDirty();
            AcademyCraft.LOGGER.debug("Registered node '{}' at {}", name, pos);
            return true;
        }

        public void unregisterNode(BlockPos pos) {
            NodeConfig config = nodeConfigurations.remove(pos);
            if (config != null) {
                nodeNameMap.remove(config.name);
                setDirty();
                AcademyCraft.LOGGER.debug("Unregistered node '{}' at {}", config.name, pos);
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
            NodeConfig config = nodeConfigurations.get(nodePos);
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
            NodeConfig config = nodeConfigurations.get(nodePos);
            if (config != null) {
                config.connectedUsers.remove(userPos);
                AcademyCraft.LOGGER.debug("Disconnected user {} from node '{}'", userPos, config.name);
                setDirty();
                return true;
            }
            return false;
        }

        public static WirelessNetworkData load(CompoundTag tag) {
            WirelessNetworkData data = new WirelessNetworkData();
            AcademyCraft.LOGGER.debug("Loading WirelessNetworkData...");
            if (tag.contains("nodes", Tag.TAG_LIST)) {
                ListTag nodesTag = tag.getList("nodes", Tag.TAG_COMPOUND);
                AcademyCraft.LOGGER.debug("Found {} nodes in NBT", nodesTag.size());
                for (int i = 0; i < nodesTag.size(); ++i) {
                    CompoundTag nodeEntryTag = nodesTag.getCompound(i);
                    if (nodeEntryTag.contains("pos", Tag.TAG_LONG)) {
                        BlockPos pos = BlockPos.of(nodeEntryTag.getLong("pos"));
                        if (nodeEntryTag.contains("config", Tag.TAG_COMPOUND)) {
                            NodeConfig config = NodeConfig.load(nodeEntryTag.getCompound("config"));
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
        public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
            ListTag nodesTag = new ListTag();
            AcademyCraft.LOGGER.debug("Saving {} node configurations...", nodeConfigurations.size());
            for (Map.Entry<BlockPos, NodeConfig> entry : nodeConfigurations.entrySet()) {
                BlockPos pos = entry.getKey();
                NodeConfig config = entry.getValue();
                if (pos != null && config != null) {
                    CompoundTag nodeEntryTag = new CompoundTag();
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
            public final String name;
            private String password;
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

                ListTag usersTag = new ListTag();
                for (Map.Entry<BlockPos, UserConfig> entry : this.connectedUsers.entrySet()) {
                    CompoundTag userTag = new CompoundTag();
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

                String name = tag.getString("name");
                String password = tag.getString("password");
                int radius = tag.getInt("radius");
                int maxConnections = tag.getInt("max_connections");

                NodeConfig loaded = new NodeConfig(name, radius, maxConnections);
                loaded.password = password;

                if (tag.contains("users", Tag.TAG_LIST)) {
                    ListTag usersTag = tag.getList("users", Tag.TAG_COMPOUND);
                    for (Tag baseTag : usersTag) {
                        if (baseTag instanceof CompoundTag userTag) {
                            if (userTag.contains("pos", Tag.TAG_LONG) && userTag.contains("weight", Tag.TAG_DOUBLE)) {
                                BlockPos pos = BlockPos.of(userTag.getLong("pos"));
                                double weightReceive = userTag.getDouble("weight_receive");
                                double weightSend = userTag.getDouble("weight_send");

                                loaded.connectedUsers.put(pos, new UserConfig(weightReceive, weightSend));
                                AcademyCraft.LOGGER.warn("User tag missing required fields: {}", userTag);
                            }
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
}