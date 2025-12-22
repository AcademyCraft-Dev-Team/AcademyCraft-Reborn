package org.academy;

import com.mojang.logging.LogUtils;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.wireless.WirelessManager;
import org.academy.internal.server.ability.PlayerCPManager;
import org.academy.internal.server.ability.PlayerDataManager;
import org.academy.internal.server.config.AbilityConfig;
import org.academy.internal.server.config.GenericConfig;
import org.academy.internal.server.world.level.storage.WorldData;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.io.File;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Logic server, not physical.
 */
@EventBusSubscriber
public final class AcademyCraftServer {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Nullable
    public static AcademyCraftConfig serverConfig;
    @Nullable
    public static WorldData worldData;
    @Nullable
    public static PlayerDataManager playerDataManager;
    @Nullable
    public static AbilityConfig abilityConfig;
    @Nullable
    public static GenericConfig genericConfig;
    @Nullable
    public static File serverConfigFile;
    @Nullable
    public static File worldDataFile;

    @Nullable
    private static ScheduledFuture<?> worldDataSaveTask;

    private AcademyCraftServer() {
    }

    @SubscribeEvent
    public static void init(ServerStartedEvent event) {
        serverConfigFile = new File(event.getServer().getServerDirectory().toFile(), "config" + File.separator + AcademyCraft.MOD_ID + "-server" + ".json");
        worldDataFile = event.getServer().getWorldPath(LevelResource.ROOT).resolve(AcademyCraft.MOD_ID + ".json").toFile();
        AcademyCraft.checkFile(serverConfigFile);
        AcademyCraft.checkFile(worldDataFile);

        serverConfig = new AcademyCraftConfig(serverConfigFile);

        AcademyCraftConfig.registerTypeHandler(AbilityConfig.KEY, AbilityConfig.Action.INSTANCE);
        AcademyCraftConfig.registerTypeHandler(GenericConfig.KEY, GenericConfig.Action.INSTANCE);
        abilityConfig = serverConfig.getConfig(AbilityConfig.KEY);
        genericConfig = serverConfig.getConfig(GenericConfig.KEY);
        serverConfig.save();

        worldData = WorldData.getWorldData(worldDataFile);
        playerDataManager = new PlayerDataManager(worldData);

        AbilitySystemServer.init(event.getServer(), playerDataManager);
        WirelessManager.initServer();

        if (worldDataSaveTask != null) worldDataSaveTask.cancel(false);

        worldDataSaveTask = AcademyCraft.EXECUTOR_SERVICE.scheduleAtFixedRate(
                () -> {
                    PlayerCPManager.flushAllToData();
                    WorldData.saveData();
                }, 5, 5, TimeUnit.MINUTES
        );
        LOGGER.info("Scheduled periodic world data saving.");
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if (worldDataSaveTask != null) worldDataSaveTask.cancel(false);
        LOGGER.info("Server stopping. Performing final data saves...");
        PlayerCPManager.flushAllToData();
        PlayerCPManager.clear();
        WorldData.saveData();
        if (serverConfig != null) serverConfig.save();
    }
}