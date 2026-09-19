package org.academy;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.api.common.profiler.AcademyProfiler;
import org.academy.api.common.util.FileUtil;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.time.TemporalService;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.api.server.wireless.WirelessManager;
import org.academy.internal.common.ability.ProficiencySkillSettings;
import org.academy.internal.common.ability.mentalout.precision.PrecisionOperationRuntime;
import org.academy.internal.common.ability.program.AbilityProgramManager;
import org.academy.internal.common.ability.program.ServerProgramScheduler;
import org.academy.internal.common.network.MusicSyncPackets;
import org.academy.internal.common.network.MusicAccountPackets;
import org.academy.internal.common.network.MagneticHookActionPacket;
import org.academy.internal.server.music.MusicRoomManager;
import org.academy.internal.server.music.ResolveService;
import org.academy.internal.server.music.ServerJukeboxManager;
import org.academy.internal.server.music.SharedAccountService;
import org.academy.internal.common.network.PlayerLeftClickSwingPacket;
import org.academy.internal.common.world.damagesource.DestroyBlocksSetting;
import org.academy.internal.common.world.damagesource.FriendlyFireSetting;
import org.academy.internal.common.world.damagesource.PvpSetting;
import org.academy.internal.server.config.AbilityConfig;
import org.academy.internal.server.config.GenericConfig;
import org.academy.internal.server.config.MusicConfig;
import org.academy.internal.server.config.DimensionEffectsConfig;
import org.academy.internal.server.entity.SurvivalDefenseRuntime;
import org.academy.internal.server.time.TemporalRuntime;
import org.academy.internal.server.world.level.storage.Player;
import org.academy.internal.server.world.level.storage.WorldData;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

/**
 * MinecraftServer, 不区分 IntegratedServer 或 DedicatedServer 喵
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
    private final MusicConfig musicConfig;
    private final DimensionEffectsConfig dimensionEffectsConfig;
    private final TemporalRuntime temporalRuntime;
    private final MinecraftServer server;
    private long lastSaveTick = 0;

    private AcademyCraftServer(MinecraftServerContext context) {
        context.setAcademyCraftServer(this);
        server = context.getMinecraftServer();
        dimensionEffectsConfig = DimensionEffectsConfig.load(server.getServerDirectory()
                .resolve("config").resolve(DimensionEffectsConfig.FILE_NAME));

        worldDataFile = server.getWorldPath(
                LevelResource.ROOT).resolve(AcademyCraft.MOD_ID + ".json"
        ).toFile();
        FileUtil.checkFile(worldDataFile);

        serverConfig = context.getAcademyCraftServerConfig();

        abilityConfig = serverConfig.getConfig(AbilityConfig.KEY);
        abilityConfig.seed("single_high_speed_electron_beam")
                .floatMap.putIfAbsent("attackDelayTicks", 10.0f);
        genericConfig = serverConfig.getConfig(GenericConfig.KEY);
        AbilitySystemServer.setDevMode(genericConfig.general.devMode);
        serverConfig.save();

        worldData = WorldData.getWorldData(worldDataFile);
        temporalRuntime = new TemporalRuntime(server);
        abilitySystemServer = new AbilitySystemServer(context, worldData, abilityConfig);
        WirelessManager.initServer();
        PvpSetting.initServer();
        FriendlyFireSetting.initServer();
        DestroyBlocksSetting.initServer();
        ProficiencySkillSettings.initServer();
        MusicSyncPackets.initServer();
        musicConfig = serverConfig.getConfig(MusicConfig.KEY);
        MusicRoomManager.initServer(server, musicConfig);
        ServerJukeboxManager.initServer(server, musicConfig);
        SharedAccountService.init(musicConfig);
        ResolveService.init(musicConfig);
        MusicAccountPackets.initServer();
        PlayerLeftClickSwingPacket.initServer();
        org.academy.internal.common.world.item.CoinItem.initServer();
        MagneticHookActionPacket.initServer();
        AbilityProgramManager.initServer();
    }

    /** Loads typed settings before publishing them to world-generation workers. */
    public static AcademyCraftConfig loadServerConfig(Path serverDirectory) {
        var file = serverDirectory.resolve("config").resolve(AcademyCraft.MOD_ID + "-server.json").toFile();
        FileUtil.checkFile(file);
        AcademyCraftConfig.registerTypeHandler(AbilityConfig.KEY, AbilityConfig.Action.INSTANCE);
        AcademyCraftConfig.registerTypeHandler(GenericConfig.KEY, GenericConfig.Action.INSTANCE);
        AcademyCraftConfig.registerTypeHandler(MusicConfig.KEY, MusicConfig.Action.INSTANCE);
        var config = new AcademyCraftConfig(file);
        config.getConfig(GenericConfig.KEY);
        return config;
    }

    @SubscribeEvent
    public static void loadConfig(ServerAboutToStartEvent event) {
        // Spawn chunks (including C2ME feature tasks) are generated before ServerStartedEvent.
        event.getServer().getAcademyCraftServerConfig();
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
        ServerProgramScheduler.clear(event.getServer());
        AbilityProgramManager.clear();
        PrecisionOperationRuntime.clear(event.getServer());
        instance.abilitySystemServer.onServerStopping();
        SurvivalDefenseRuntime.shutdown();
        instance.temporalRuntime.shutdown();
        LOGGER.info("Server stopping. Performing final data saves...");
        instance.saveData();
        instance.serverConfig.save();
        // 关服前落盘音乐室，避免重启后房间消失喵.
        MusicRoomManager.saveState();
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

    public MusicConfig getMusicConfig() {
        return musicConfig;
    }

    public DimensionEffectsConfig getDimensionEffectsConfig() {
        return dimensionEffectsConfig;
    }

    /**
     * World-generation gate for the imag-phase lake feature. Worldgen runs on worker threads, so this
     * reads the shared config independently of the gameplay runtime's later initialization.
     */
    public static boolean isImagPhaseGenerationEnabled(net.minecraft.server.level.ServerLevel level) {
        if (level == null) return true;
        return isImagPhaseGenerationEnabledForServer(level.getServer());
    }

    static boolean isImagPhaseGenerationEnabledForServer(MinecraftServerContext context) {
        return context.getAcademyCraftServerConfig().<GenericConfig>getConfig(GenericConfig.KEY)
                .worldgen.generateImagPhaseLakes;
    }

    public TemporalService getTemporalService() {
        return temporalRuntime;
    }

    public AbilityConfig getAbilityConfig() {
        return abilityConfig;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        var instance = server.getAcademyCraftServer();
        SurvivalDefenseRuntime.tickAll(server);
        ServerProgramScheduler.tick(server);
        MusicRoomManager.tick(server);
        ServerJukeboxManager.tick(server);
        long currentTick = server.getTickCount();
        if (currentTick - instance.lastSaveTick >= SAVE_INTERVAL_TICKS) {
            instance.lastSaveTick = currentTick;
            var snapshot = instance.createSnapshotAndClean();
            if (snapshot == null) return;
            instance.writeToFile(snapshot);
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
