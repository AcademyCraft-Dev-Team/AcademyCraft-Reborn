package org.academy;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.api.common.util.FileUtil;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.api.server.wireless.WirelessManager;
import org.academy.internal.server.config.AbilityConfig;
import org.academy.internal.server.config.GenericConfig;
import org.academy.internal.server.world.level.storage.Player;
import org.academy.internal.server.world.level.storage.WorldData;
import org.academy.internal.common.world.damagesource.FriendlyFireSetting;
import org.academy.internal.common.world.damagesource.DestroyBlocksSetting;
import org.academy.internal.common.network.MusicSyncPackets;
import org.academy.api.common.profiler.AcademyProfiler;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * MinecraftServer, 不区分 IntegratedServer 或 DedicatedServer
 */
@EventBusSubscriber
public final class AcademyCraftServer {
    private static final Logger LOGGER = AcademyCraft.getLogger();
    private static final long SAVE_INTERVAL_TICKS = 20 * 60 * 5;
    private final File worldDataFile;
    private final AcademyCraftConfig serverConfig;
    private final WorldData worldData;
    private final AbilitySystemServer abilitySystemServer;
    private final AbilityConfig abilityConfig;
    private final GenericConfig genericConfig;
    private final MinecraftServer server;
    private long lastSaveTick = 0;

    /**
     * 一个 MinecraftServer 实例对应一个 MinecraftServerContext
     */
    private AcademyCraftServer(MinecraftServerContext context) {
        context.setAcademyCraftServer(this);
        server = context.getMinecraftServer();

        var serverConfigFile = new File(
                server.getServerDirectory().toFile(),
                "config" + File.separator + AcademyCraft.MOD_ID + "-server" + ".json"
        );
        worldDataFile = server.getWorldPath(
                LevelResource.ROOT).resolve(AcademyCraft.MOD_ID + ".json"
        ).toFile();
        FileUtil.checkFile(serverConfigFile);
        FileUtil.checkFile(worldDataFile);

        serverConfig = new AcademyCraftConfig(serverConfigFile);

        AcademyCraftConfig.registerTypeHandler(AbilityConfig.KEY, AbilityConfig.Action.INSTANCE);
        AcademyCraftConfig.registerTypeHandler(GenericConfig.KEY, GenericConfig.Action.INSTANCE);
        abilityConfig = serverConfig.getConfig(AbilityConfig.KEY);
        abilityConfig.skills
                .computeIfAbsent("single_high_speed_electron_beam", _ -> new AbilityConfig.SkillSettings())
                .floatMap.putIfAbsent("attackDelayTicks", 10.0f);
        genericConfig = serverConfig.getConfig(GenericConfig.KEY);
        serverConfig.save();

        worldData = WorldData.getWorldData(worldDataFile);
        abilitySystemServer = new AbilitySystemServer(context, worldData, abilityConfig);
        WirelessManager.initServer();
        FriendlyFireSetting.initServer();
        DestroyBlocksSetting.initServer();
        MusicSyncPackets.initServer();

        NeoForge.EVENT_BUS.addListener(this::onServerTick);
    }

    @SubscribeEvent
    public static void init(ServerStartedEvent event) {
        new AcademyCraftServer(event.getServer());
        AcademyProfiler.registerThread(event.getServer().getRunningThread());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        var context = event.getServer();
        var instance = context.getAcademyCraftServer();
        instance.abilitySystemServer.onServerStopping();
        LOGGER.info("Server stopping. Performing final data saves...");
        instance.saveData();
        instance.serverConfig.save();
        AcademyProfiler.stopSampling();
        AcademyProfiler.stopZoneCapture();
        AcademyProfiler.unregisterThread(context.getRunningThread());
    }

    public AbilitySystemServer getAbilitySystemServer() {
        return abilitySystemServer;
    }

    public GenericConfig getGenericConfig() {
        return genericConfig;
    }

    public AbilityConfig getAbilityConfig() {
        return abilityConfig;
    }

    public void onServerTick(ServerTickEvent.Post event) {
        long currentTick = server.getTickCount();
        if (currentTick - lastSaveTick >= SAVE_INTERVAL_TICKS) {
            lastSaveTick = currentTick;
            var snapshot = createSnapshotAndClean();
            if (snapshot == null) return;
            writeToFile(snapshot);
        }
    }

    private void saveData() {
        LOGGER.info("Saving world data...");
        var snapshot = createSnapshotAndClean();
        writeToFile(snapshot);
    }

    /**
     * @return JSON快照，没有脏数据则返回null
     */
    private @Nullable String createSnapshotAndClean() {
        var hasDirtyData = worldData.getPlayers().values().stream()
                .anyMatch(Player::isDirty);
        if (!hasDirtyData) return null;

        String jsonSnapshot;
        try {
            var gson = WorldData.createGson();
            jsonSnapshot = gson.toJson(worldData);
        } catch (Exception e) {
            LOGGER.error("Failed to serialize WorldData", e);
            return null;
        }

        worldData.getPlayers().values().forEach(Player::clean);
        return jsonSnapshot;
    }

    private void writeToFile(@Nullable String jsonSnapshot) {
        if (jsonSnapshot == null) return;
        try (var writer = new FileWriter(worldDataFile)) {
            writer.write(jsonSnapshot);
            LOGGER.debug("WorldData saved successfully.");
        } catch (IOException e) {
            LOGGER.error("Failed to write WorldData to disk", e);
        }
    }
}
