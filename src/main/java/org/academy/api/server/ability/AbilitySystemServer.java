package org.academy.api.server.ability;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftServer;
import org.academy.api.common.ability.*;
import org.academy.api.common.network.future.HandlePayload;
import org.academy.api.common.network.packet.S2CPacket;
import org.academy.api.common.registries.Registries;
import org.academy.api.common.util.MathUtil;
import org.academy.api.common.wireless.WirelessUser;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.world.level.block.entity.AbilityDeveloperBlockEntity;
import org.academy.internal.server.ability.PlayerDataManager;
import org.academy.internal.server.world.level.storage.Player;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.academy.api.server.ability.AbilitySystemServer.SyncType.COMPUTING_POWER;

public class AbilitySystemServer {
    public static final Map<UUID, LivePlayer> LIVE_PLAYER_MAP = new ConcurrentHashMap<>();
    private static final List<Runnable> pendingTasks = new CopyOnWriteArrayList<>();
    private static PlayerDataManager playerDataManager;
    public static volatile MinecraftServer minecraftServer;
    public static volatile ScheduledFuture<?> scheduledFuture;
    public static boolean paused;

    public static void init(final MinecraftServer server, PlayerDataManager manager) {
        playerDataManager = manager;
        AcademyCraftServer.SERVER_FUTURE_MANAGER.registerPayloadHandler(AbilitySystemServer.class);
        minecraftServer = server;

        for (var category : Registries.ABILITY_CATEGORIES) {
            category.initServer(server);
        }
        for (var skill : Registries.SKILLS) {
            skill.initServer(server);
        }

        scheduledFuture = AcademyCraft.executorService.scheduleAtFixedRate(
                () -> {
                    try {
                        AbilitySystemTicker.tick();
                    } catch (Throwable e) {
                        AcademyCraft.LOGGER.error(e.getMessage());
                    }
                }, 0, 50, TimeUnit.MILLISECONDS
        );
    }

    public static Player getPlayerData(UUID uuid) {
        return playerDataManager.getData(uuid);
    }

    @SuppressWarnings("resource")
    @HandlePayload
    public static AcquireCategoryPacket.Response handleAcquireCategory(AcquireCategoryPacket payload) {
        var player = payload.getPacketListener().getPlayer();
        var userPos = payload.userPos;
        if (player.position().distanceToSqr(Vec3.atCenterOf(userPos)) > 64.0) {
            return new AcquireCategoryPacket.Response(Collections.singletonList("Error: You are too far away."));
        }

        var level = player.serverLevel();
        var be = level.getBlockEntity(userPos);
        if (be instanceof AbilityDeveloperBlockEntity blockEntity) {
            var outputList = new ArrayList<String>();
            var energyStored = blockEntity.getEnergyStored();
            if (energyStored > 10_000) {
                blockEntity.setEnergyStored(energyStored - 10_000);
                var weightedRandom = new MathUtil.WeightedRandom<AbilityCategory>();
                for (var category : Registries.ABILITY_CATEGORIES) {
                    if (category != AbilityCategories.LEVEL0.get()) {
                        weightedRandom.addItem(category, category.getProbability());
                    }
                }
                var abilityCategory = weightedRandom.getRandomItem();
                if (abilityCategory != null) {
                    setPlayerAbilityCategory(player.getUUID(), abilityCategory);
                    outputList.add("Learning complete. Type 'exit' to shut down, then reopen the screen to proceed.");
                } else {
                    outputList.add("Error: No ability category could be selected.");
                    AcademyCraft.LOGGER.error("WeightedRandom returned null for ability category selection.");
                }
            } else {
                outputList.add("Insufficient energy to develop ability.");
            }
            return new AcquireCategoryPacket.Response(outputList);
        }
        return new AcquireCategoryPacket.Response(Collections.singletonList("Error: Block is not an Ability Developer."));
    }

    @HandlePayload
    @SuppressWarnings("resource")
    public static LearnSkillPacket.Response handleLearnSkill(LearnSkillPacket payload) {
        var player = payload.getPacketListener().getPlayer();
        var userPos = payload.userPos;
        if (player.position().distanceToSqr(Vec3.atCenterOf(userPos)) > 64.0) {
            return new LearnSkillPacket.Response(false);
        }

        var level = player.serverLevel();
        var skillKey = payload.skillName;
        var be = level.getBlockEntity(userPos);
        if (be instanceof WirelessUser user) {
            var skill = Registries.SKILLS.get(ResourceLocation.parse(skillKey));
            if (skill != null) {
                var energy = skill.getEnergyCostToLearn();
                var depLearned = true;
                for (var dep : skill.getDependencies()) {
                    if (!getPlayerSkills(player.getUUID()).contains(dep.getKeyString())) {
                        depLearned = false;
                        break;
                    }
                }
                var learned = getPlayerData(player.getUUID()).getSkills().contains(skillKey);
                var canLearn = user.getEnergyStored() >= energy && depLearned && !learned;
                if (canLearn) {
                    user.extractEnergy(energy, false);
                    addPlayerSkill(player.getUUID(), skillKey);
                }
                return new LearnSkillPacket.Response(canLearn);
            }
        }
        return new LearnSkillPacket.Response(false);
    }

    public static void scheduleFullPlayerSync(final UUID uuid) {
        var syncType = SyncType.values();
        for (var type : syncType) {
            schedulePlayerSync(uuid, type);
        }
    }

    public static AbilityCategory getPlayerAbilityCategory(UUID uuid) {
        var categoryKey = ResourceLocation.parse(getPlayerData(uuid).getAbilityCategory());
        var category = Registries.ABILITY_CATEGORIES.get(categoryKey);
        return category != null ? category : AbilityCategories.LEVEL0.get();
    }

    public static void setPlayerAbilityCategory(UUID uuid, AbilityCategory abilityCategory) {
        var categoryKey = Registries.ABILITY_CATEGORIES.getKey(abilityCategory);
        if (categoryKey != null) {
            getPlayerData(uuid).setAbilityCategory(categoryKey.toString());
            schedulePlayerSync(uuid, SyncType.ABILITY_CATEGORY);
        }
    }

    public static HashSet<String> getPlayerSkills(UUID uuid) {
        return getPlayerData(uuid).getSkills();
    }

    public static void addPlayerSkill(UUID uuid, String skillKey) {
        Player playerData = getPlayerData(uuid);
        if (playerData.getSkills().add(skillKey)) {
            var skill = Registries.SKILLS.get(ResourceLocation.parse(skillKey));
            if (skill != null) {
                addPlayerSkillData(uuid, skillKey, skill.getDefaultSkillData());
            }
            playerData.markDirty();
            schedulePlayerSync(uuid, SyncType.SKILLS);
        }
    }

    public static void addPlayerSkillData(UUID uuid, String skillKey, Player.SkillData skillData) {
        Player playerData = getPlayerData(uuid);
        var oldValue = playerData.getSkillData().put(skillKey, skillData);
        if (!Objects.equals(oldValue, skillData)) {
            playerData.markDirty();
        }
    }

    public static void removePlayerSkill(UUID uuid, String skillKey) {
        Player playerData = getPlayerData(uuid);
        if (playerData.getSkills().remove(skillKey)) {
            playerData.getSkillData().remove(skillKey);
            playerData.markDirty();
            schedulePlayerSync(uuid, SyncType.SKILLS);
        }
    }

    public static int getPlayerLevel(UUID uuid) {
        return getPlayerData(uuid).getLevel();
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public static <T extends Player.SkillData> T getPlayerSkillData(UUID uuid, String skillKey) {
        return (T) getPlayerData(uuid).getSkillData().get(skillKey);
    }

    public static float getPlayerSkillExp(UUID uuid, String skillKey) {
        var skillData = getPlayerData(uuid).getSkillData().get(skillKey);
        if (skillData == null) {
            return 0;
        } else {
            return skillData.exp;
        }
    }

    public static void setPlayerSkillExp(UUID uuid, String skillKey, float exp) {
        var skillData = getPlayerData(uuid).getSkillData().get(skillKey);
        if (skillData != null) {
            skillData.exp = exp;
            var player = LIVE_PLAYER_MAP.get(uuid);
            if (player != null) {
                player.connection.send(new S2CPacket(new ExpSyncPacket(skillKey, skillData.exp)));
            }
        }
    }

    public static void setPlayerLevel(UUID uuid, int level) {
        getPlayerData(uuid).setLevel(level);
    }

    public static float getPlayerComputingPower(UUID uuid) {
        return getPlayerData(uuid).getComputingPower();
    }

    public static void setPlayerComputingPower(UUID uuid, float power) {
        getPlayerData(uuid).setComputingPower(power);
        schedulePlayerSync(uuid, COMPUTING_POWER);
    }

    public static float getPlayerMaxComputingPower(UUID uuid) {
        return getPlayerData(uuid).getMaxComputingPower();
    }

    public static void setPlayerMaxComputingPower(UUID uuid, float power) {
        getPlayerData(uuid).setMaxComputingPower(power);
        schedulePlayerSync(uuid, SyncType.MAX_COMPUTING_POWER);
    }

    public static float getPlayerComputingPowerRecoverySpeed(UUID uuid) {
        return getPlayerData(uuid).getComputingPowerRecoverySpeed();
    }

    public static void setPlayerComputingPowerRecoverySpeed(UUID uuid, float speed) {
        getPlayerData(uuid).setComputingPowerRecoverySpeed(speed);
    }

    public static float getPlayerAdditionalComputingPower(UUID uuid) {
        Player playerData = getPlayerData(uuid);
        if (playerData == null) {
            return -1;
        } else {
            var livePlayer = LIVE_PLAYER_MAP.get(uuid);
            return livePlayer != null ? livePlayer.additionalComputingPower : 0;
        }
    }

    public static void setPlayerAdditionalComputingPower(UUID uuid, float power) {
        if (getPlayerData(uuid) != null) {
            var livePlayer = LIVE_PLAYER_MAP.get(uuid);
            if (livePlayer != null) {
                livePlayer.additionalComputingPower = power;
            }
        }
    }

    public static float getDamageMultiplier() {
        return AcademyCraftServer.abilityConfig.damageMultiplier;
    }

    public static void schedulePlayerSync(final UUID uuid, final SyncType syncType) {
        if (LIVE_PLAYER_MAP.containsKey(uuid)) {
            LIVE_PLAYER_MAP.get(uuid).syncQueue.add(syncType);
        }
    }

    public static void addTask(Runnable runnable) {
        pendingTasks.add(runnable);
    }

    public enum SyncType {
        LEVEL,
        COMPUTING_POWER,
        MAX_COMPUTING_POWER,
        ABILITY_CATEGORY,
        SKILLS,
    }

    public static final class AbilitySystemTicker {
        public static void tick() {
            if (paused) return;
            pendingTasks.forEach(Runnable::run);
            pendingTasks.clear();
            for (var player : LIVE_PLAYER_MAP.values()) {
                tickPlayer(player);
            }
        }

        public static void tickPlayer(LivePlayer player) {
            final var connection = player.connection;
            final var uuid = player.uuid;
            var syncQueue = player.syncQueue;
            var levelChanged = syncQueue.contains(SyncType.LEVEL);
            var currentComputingPowerChanged = syncQueue.contains(SyncType.COMPUTING_POWER);
            var maxComputingPowerChanged = syncQueue.contains(SyncType.MAX_COMPUTING_POWER);
            var abilityCategoryChanged = syncQueue.contains(SyncType.ABILITY_CATEGORY);
            var skillsChanged = syncQueue.contains(SyncType.SKILLS);

            var playerCategory = getPlayerAbilityCategory(uuid);
            var categoryKey = Registries.ABILITY_CATEGORIES.getKey(playerCategory);

            var packet = new PlayerSyncPacket(
                    levelChanged, getPlayerLevel(uuid),
                    currentComputingPowerChanged, getPlayerComputingPower(uuid),
                    maxComputingPowerChanged, getPlayerMaxComputingPower(uuid),
                    abilityCategoryChanged, categoryKey != null ? categoryKey.toString() : "academy:level0",
                    skillsChanged, getPlayerSkills(uuid)
            );
            connection.send(new S2CPacket(packet));
            player.syncQueue.clear();

            final var currentComputingPower = getPlayerComputingPower(uuid);
            final var maxComputingPower = getPlayerMaxComputingPower(uuid);
            final var computingPowerRecoverySpeed = getPlayerComputingPowerRecoverySpeed(uuid);
            if (currentComputingPower < maxComputingPower) {
                setPlayerComputingPower(uuid, Math.min(maxComputingPower, currentComputingPower + computingPowerRecoverySpeed));
            }

            var learned = getPlayerSkills(uuid).size();
            var all = playerCategory.getSkills().size();
            float progress;
            if (all == 0) progress = 100.0f;
            else progress = (float) learned / all;
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MODID)
    public static final class ServerLifecycleHooks {
        @SubscribeEvent
        public static void tickMinecraftServerThread(ServerTickEvent.Pre event) {
            var server = event.getServer();
            server.getPlayerList().getPlayers().forEach(serverPlayer -> {
                final var uuid = serverPlayer.getUUID();
                if (!LIVE_PLAYER_MAP.containsKey(uuid)) {
                    AcademyCraftServer.playerDataManager.onPlayerLogin(serverPlayer);
                    LIVE_PLAYER_MAP.put(uuid, new LivePlayer(
                            uuid, serverPlayer.connection
                            )
                    );
                    scheduleFullPlayerSync(uuid);
                }
            });
            var onlinePlayerUUIDs = server.getPlayerList().getPlayers().stream()
                    .map(Entity::getUUID)
                    .collect(Collectors.toSet());

            LIVE_PLAYER_MAP.keySet().removeIf(uuid -> !onlinePlayerUUIDs.contains(uuid));
        }
    }

    public static class LivePlayer {
        public final UUID uuid;
        public final ConcurrentLinkedQueue<SyncType> syncQueue = new ConcurrentLinkedQueue<>();
        private final ServerGamePacketListenerImpl connection;
        public float additionalComputingPower;

        public LivePlayer(final UUID newUuid, final ServerGamePacketListenerImpl newConnection) {
            uuid = newUuid;
            connection = newConnection;
        }
    }

    public static void registerContext(ServerContext serverContext) {
        NeoForge.EVENT_BUS.register(serverContext);
        AcademyCraftServer.SERVER_NETWORK_MANAGER.registerPacketListener(serverContext);
    }

    public static void unregisterContext(ServerContext serverContext) {
        NeoForge.EVENT_BUS.unregister(serverContext);
        AcademyCraftServer.SERVER_NETWORK_MANAGER.unregisterPacketListener(serverContext);
    }
}