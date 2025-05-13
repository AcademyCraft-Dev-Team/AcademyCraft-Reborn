package org.academy.api.server.ability;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftServer;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.AbilitySystem;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.network.FriendlyByteBufSerializers;
import org.academy.api.common.network.Packets;
import org.academy.api.common.network.packet.S2CPacket;
import org.academy.api.common.util.MathUtil;
import org.academy.api.common.wireless.WirelessUser;
import org.academy.api.server.network.FutureManagerServer;
import org.academy.api.server.network.NetworkSystemServer;
import org.academy.internal.common.ability.builtin.level0.Level0;
import org.academy.internal.server.world.level.storage.WorldData;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.academy.api.common.ability.AbilitySystem.ABILITY_CATEGORY_MAP;
import static org.academy.api.common.ability.AbilitySystem.SKILL_MAP;
import static org.academy.api.server.ability.AbilitySystemServer.SyncType.*;

public class AbilitySystemServer {
    public static final Map<UUID, Player> LIVE_PLAYER_MAP = new ConcurrentHashMap<>();
    private static final List<Runnable> RUNNABLE_LIST = new CopyOnWriteArrayList<>();
    private static Map<UUID, WorldData.Player> playerMap;
    public static volatile MinecraftServer minecraftServer;
    public static volatile ScheduledFuture<?> scheduledFuture;
    public static boolean paused;

    public static void init(final MinecraftServer server) {
        registerPacketHandler();
        minecraftServer = server;
        playerMap = AcademyCraftServer.worldData.getPlayers();
        for (AbilityCategory abilityCategory : ABILITY_CATEGORY_MAP.values()) {
            abilityCategory.initServer(server);
            for (Skill skill : abilityCategory.skillList) {
                skill.initServer(server);
            }
        }
        scheduledFuture = AcademyCraft.executorService.scheduleAtFixedRate(
                () -> {
                    try {
                        AbilitySystemServerThread.tick();
                    } catch (Exception e) {
                        AcademyCraft.LOGGER.error(e.getMessage());
                    }
                }, 0, 50, TimeUnit.MILLISECONDS
        );
    }

    @SuppressWarnings("resource")
    public static void registerPacketHandler() {
        NetworkSystemServer.registerC2SPacketHandler(Packets.C2S_ACQUIRE_CATEGORY, (listener, packet) -> {
            ServerPlayer player = listener.player;
            ServerLevel level = player.serverLevel();
            int id = packet.friendlyByteBuf.readVarInt();
            BlockPos userPos = packet.friendlyByteBuf.readBlockPos();
            BlockEntity be = level.getBlockEntity(userPos);
            if (be instanceof WirelessUser user) {
                List<String> outputList = new ArrayList<>();
                int energyStored = user.getEnergyStored();
                if (energyStored > 10_000) {
                    user.extractEnergy(10_000, false);
                    MathUtil.WeightedRandom<AbilityCategory> weightedRandom = new MathUtil.WeightedRandom<>();
                    for (AbilityCategory abilityCategory : ABILITY_CATEGORY_MAP.values()) {
                        if (abilityCategory != Level0.INSTANCE) {
                            weightedRandom.addItem(abilityCategory, abilityCategory.probability);
                        }
                    }
                    AbilityCategory abilityCategory = weightedRandom.getRandomItem();
                    setPlayerAbilityCategory(player.getUUID(), abilityCategory);
                    outputList.add("Learning complete. Type 'exit' to shut down, then reopen the screen to proceed.");
                } else {
                    outputList.add("Insufficient energy available.");
                }
                FutureManagerServer.sendResult(listener, id, outputList);
            }
        });
        NetworkSystemServer.registerC2SPacketHandler(Packets.C2S_LEARN_SKILL, (listener, packet) -> {
            ServerPlayer player = listener.player;
            ServerLevel level = player.serverLevel();
            int id = packet.friendlyByteBuf.readVarInt();
            String skillName = packet.friendlyByteBuf.readUtf();
            BlockPos userPos = packet.friendlyByteBuf.readBlockPos();
            BlockEntity be = level.getBlockEntity(userPos);
            if (be instanceof WirelessUser user) {
                if (SKILL_MAP.containsKey(skillName)) {
                    Skill skill = SKILL_MAP.get(skillName);
                    int energy = skill.energy;
                    boolean depLearned = true;
                    for (Skill dep : skill.dependencies) {
                        if (!getPlayerSkills(player.getUUID()).contains(dep.name)) {
                            depLearned = false;
                        }
                    }
                    boolean learned = playerMap.get(player.getUUID()).getSkills().contains(skillName);
                    boolean can = user.getEnergyStored() > energy && depLearned && !learned;
                    if (can) {
                        user.extractEnergy(energy, false);
                        addPlayerSkill(player.getUUID(), skillName);
                    }
                    FutureManagerServer.sendResult(listener, id, can);
                }
            }
        });
    }

    public static void addAllPlayerSyncTask(final UUID uuid) {
        Field[] fields = SyncType.class.getDeclaredFields();

        for (Field field : fields) {
            int modifiers = field.getModifiers();

            if (Modifier.isPublic(modifiers) &&
                    Modifier.isStatic(modifiers) &&
                    Modifier.isFinal(modifiers) &&
                    field.getType().equals(String.class)) {

                try {
                    String value = (String) field.get(null);
                    addPlayerSyncTask(uuid, value);
                } catch (IllegalAccessException ignored) {
                }
            }
        }
    }

    public static AbilityCategory getPlayerAbilityCategory(UUID uuid) {
        return ABILITY_CATEGORY_MAP.get(playerMap.get(uuid).getAbilityCategory());
    }

    public static void setPlayerAbilityCategory(UUID uuid, AbilityCategory abilityCategory) {
        playerMap.get(uuid).setAbilityCategory(abilityCategory.name);
        addPlayerSyncTask(uuid, SyncType.ABILITY_CATEGORY);
    }

    public static Set<String> getPlayerSkills(UUID uuid) {
        return playerMap.get(uuid).getSkills();
    }

    public static void addPlayerSkill(UUID uuid, String skill) {
        playerMap.get(uuid).getSkills().add(skill);
        Skill skillI = SKILL_MAP.get(skill);
        if (skillI != null) {
            addPlayerSkillData(uuid, skill, skillI.getDefaultSkillData());
        }
        addPlayerSyncTask(uuid, SyncType.SKILLS);
    }

    public static void addPlayerSkillData(UUID uuid, String skill, WorldData.Player.SkillData skillData) {
        playerMap.get(uuid).getSkillData().put(skill, skillData);
    }

    public static void removePlayerSkill(UUID uuid, String skill) {
        playerMap.get(uuid).getSkills().remove(skill);
        addPlayerSyncTask(uuid, SyncType.SKILLS);
    }

    public static int getPlayerLevel(UUID uuid) {
        return playerMap.get(uuid).getLevel();
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public static <T extends WorldData.Player.SkillData> T getPlayerSkillData(UUID uuid, String skill) {
        playerMap.get(uuid).getSkillData().get(skill);
        return (T) playerMap.get(uuid).getSkillData().get(skill);
    }

    public static float getPlayerSkillExp(UUID uuid, String skill) {
        WorldData.Player.SkillData skillData = playerMap.get(uuid).getSkillData().get(skill);
        if (skillData == null) {
            return 0;
        } else {
            return skillData.exp;
        }
    }

    public static void setPlayerSkillExp(UUID uuid, String skill, float exp) {
        WorldData.Player.SkillData skillData = playerMap.get(uuid).getSkillData().get(skill);
        if (skillData != null) {
            skillData.exp = exp;
            Player player = LIVE_PLAYER_MAP.get(uuid);
            if (player != null) {
                FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.buffer());
                friendlyByteBuf.writeUtf(skill);
                friendlyByteBuf.writeFloat(skillData.exp);
                player.packetConsumer.accept(new S2CPacket(
                        Packets.S2C_EXP_SYNC, friendlyByteBuf
                ));
            }
        }
    }

    public static void setPlayerLevel(UUID uuid, int level) {
        playerMap.get(uuid).setLevel(level);
    }

    public static float getPlayerComputingPower(UUID uuid) {
        return playerMap.get(uuid).getComputingPower();
    }

    public static void setPlayerComputingPower(UUID uuid, float power) {
        playerMap.get(uuid).setComputingPower(power);
        addPlayerSyncTask(uuid, COMPUTING_POWER);
    }

    public static float getPlayerMaxComputingPower(UUID uuid) {
        return playerMap.get(uuid).getMaxComputingPower();
    }

    public static void setPlayerMaxComputingPower(UUID uuid, float power) {
        playerMap.get(uuid).setMaxComputingPower(power);
        addPlayerSyncTask(uuid, SyncType.MAX_COMPUTING_POWER);
    }

    public static float getPlayerComputingPowerRecoverySpeed(UUID uuid) {
        return playerMap.get(uuid).getComputingPowerRecoverySpeed();
    }

    public static void setPlayerComputingPowerRecoverySpeed(UUID uuid, float speed) {
        playerMap.get(uuid).setComputingPowerRecoverySpeed(speed);
    }

    /**
     * You must check return.
     */
    public static float getPlayerAdditionalComputingPower(UUID uuid) {
        if (!playerMap.containsKey(uuid)) {
            return -1;
        } else {
            return LIVE_PLAYER_MAP.get(uuid).additionalComputingPower;
        }
    }

    public static void setPlayerAdditionalComputingPower(UUID uuid, float power) {
        if (playerMap.containsKey(uuid)) {
            LIVE_PLAYER_MAP.get(uuid).additionalComputingPower = power;
        }
    }

    public static float getDamageMultiplier() {
        return AcademyCraftServer.serverConfig.getAbility().getDamageMultiplier();
    }

    public static void addPlayerSyncTask(final UUID uuid, final String syncType) {
        if (LIVE_PLAYER_MAP.containsKey(uuid)) {
            LIVE_PLAYER_MAP.get(uuid).syncQueue.add(syncType);
        }
    }

    public static void addTask(Runnable runnable) {
        RUNNABLE_LIST.add(runnable);
    }

    public static final class SyncType {
        public static final String COMPUTING_POWER = "computingPower";
        public static final String MAX_COMPUTING_POWER = "maxComputingPower";
        public static final String ABILITY_CATEGORY = "abilityCategory";
        public static final String SKILLS = "skills";
        public static final String LEVEL = "level";
    }

    public static final class AbilitySystemServerThread {
        public static void tick() {
            if (paused) return;
            for (Runnable runnable : RUNNABLE_LIST) {
                runnable.run();
                RUNNABLE_LIST.remove(runnable);
            }
            for (Player player : LIVE_PLAYER_MAP.values()) {
                tickPlayer(player);
            }
        }

        public static void tickPlayer(Player player) {final Consumer<Packet<?>> packetConsumer = player.packetConsumer;
            final UUID uuid = player.uuid;
            player.syncQueue.forEach(syncType -> {
                switch (syncType) {
                    case COMPUTING_POWER -> packetConsumer.accept(
                            new S2CPacket(
                                    Packets.S2C_COMPUTING_POWER_SYNC,
                                    new FriendlyByteBuf(Unpooled.buffer()
                                            .writeFloat(getPlayerComputingPower(uuid))
                                    )
                            )
                    );
                    case ABILITY_CATEGORY -> packetConsumer.accept(
                            new S2CPacket(
                                    Packets.S2C_ABILITY_CATEGORY_SYNC,
                                    new FriendlyByteBuf(Unpooled.buffer())
                                            .writeUtf(getPlayerAbilityCategory(uuid).name)
                            )
                    );
                    case MAX_COMPUTING_POWER -> packetConsumer.accept(
                            new S2CPacket(
                                    Packets.S2C_MAX_COMPUTING_POWER_SYNC,
                                    new FriendlyByteBuf(Unpooled.buffer()
                                            .writeFloat(getPlayerMaxComputingPower(uuid))
                                    )
                            )
                    );
                    case SKILLS -> {
                        ArrayList<Skill> skillList = new ArrayList<>();
                        for (String string : getPlayerSkills(uuid)) {
                            skillList.add(AbilitySystem.SKILL_MAP.get(string));
                        }
                        FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.buffer());
                        FriendlyByteBufSerializers.SKILL_ARRAY_LIST_FRIENDLY_BYTE_BUF_SERIALIZER
                                .serialize(friendlyByteBuf, skillList);
                        packetConsumer.accept(
                                new S2CPacket(Packets.S2C_SKILLS_SYC, friendlyByteBuf)
                        );
                    }
                    case LEVEL -> packetConsumer.accept(
                            new S2CPacket(Packets.S2C_LEVEL_SYNC, new FriendlyByteBuf(Unpooled.buffer())
                                    .writeVarInt(getPlayerLevel(uuid)))
                    );
                }
            });
            player.syncQueue.clear();

            final float currentComputingPower = getPlayerComputingPower(uuid);
            final float maxComputingPower = getPlayerMaxComputingPower(uuid);
            final float computingPowerRecoverySpeed = getPlayerComputingPowerRecoverySpeed(uuid);
            if (currentComputingPower < maxComputingPower) {
                setPlayerComputingPower(uuid, currentComputingPower + computingPowerRecoverySpeed);
            }

            AbilityCategory abilityCategory = getPlayerAbilityCategory(uuid);
            int learned = getPlayerSkills(uuid).size();
            int all = abilityCategory.skillList.size();
            float progress;
            if (all == 0) progress = 100.0f;
            else progress = (float) learned / all;
        }
    }

    public static final class MinecraftServerThread {
        public static void initPlayer(ServerPlayer player) {
            if (AcademyCraftServer.worldData == null) {
                return;
            }
            if (!playerMap.containsKey(player.getUUID())) {
                WorldData.Player data = new WorldData.Player();
                playerMap.put(player.getUUID(), data);
                setPlayerLevel(player.getUUID(), 0);
                setPlayerAbilityCategory(player.getUUID(), Level0.INSTANCE);
                AcademyCraft.LOGGER.debug("Finish init player.");
            } else {
                AcademyCraft.LOGGER.debug("Player already exists.");
            }
        }

        public static void tickMinecraftServerThread(final MinecraftServer server) {
            server.getPlayerList().getPlayers().forEach(serverPlayer -> {
                final UUID uuid = serverPlayer.getUUID();
                if (!LIVE_PLAYER_MAP.containsKey(uuid)) {
                    LIVE_PLAYER_MAP.put(uuid, new Player(
                            uuid, playerMap.get(uuid), packet -> serverPlayer.connection.send(packet))
                    );
                    addAllPlayerSyncTask(uuid);
                }
            });
            Set<UUID> onlinePlayerUUIDs = server.getPlayerList().getPlayers().stream()
                    .map(Entity::getUUID)
                    .collect(Collectors.toSet());

            LIVE_PLAYER_MAP.keySet().removeIf(uuid -> !onlinePlayerUUIDs.contains(uuid));
        }
    }

    public static class Player {
        public final UUID uuid;
        public final WorldData.Player data;
        public final ConcurrentLinkedQueue<String> syncQueue = new ConcurrentLinkedQueue<>();
        private final Consumer<Packet<?>> packetConsumer;
        public float additionalComputingPower;

        public Player(final UUID uuid, final WorldData.Player data, final Consumer<Packet<?>> packetConsumer) {
            this.uuid = uuid;
            this.data = data;
            this.packetConsumer = packetConsumer;
        }
    }

    public static void registerContext(ServerContext serverContext) {
        AcademyCraft.EVENT_BUS.register(serverContext);
    }

    public static void unregisterContext(ServerContext serverContext) {
        AcademyCraft.EVENT_BUS.unregister(serverContext);
    }
}