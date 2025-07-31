package org.academy;

import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.academy.api.common.network.NetworkManager;
import org.academy.api.common.network.future.FutureManager;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.network.future.FutureManagerServer;
import org.academy.api.server.wireless.WirelessManager;
import org.academy.internal.common.world.item.ImagiphaseDowsingRodItem;
import org.academy.internal.server.ability.PlayerDataManager;
import org.academy.internal.server.config.AbilityConfig;
import org.academy.internal.server.config.GenericConfig;
import org.academy.internal.server.world.level.storage.WorldData;

import java.io.File;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@EventBusSubscriber(modid = AcademyCraft.MODID)
public final class AcademyCraftServer {
    public static AcademyCraftConfig serverConfig;
    public static WorldData worldData;
    public static PlayerDataManager playerDataManager;
    public static AbilityConfig abilityConfig;
    public static GenericConfig genericConfig;
    public static File serverConfigFile;
    public static File worldDataFile;
    public static final FutureManager FUTURE_MANAGER = new FutureManager();
    public static final NetworkManager SERVER_NETWORK_MANAGER = new NetworkManager();
    public static final FutureManagerServer SERVER_FUTURE_MANAGER = new FutureManagerServer(FUTURE_MANAGER);

    private static ScheduledFuture<?> worldDataSaveTask;

    @SubscribeEvent
    public static void init(ServerStartedEvent event) {
        AcademyCraft.init();

        SERVER_NETWORK_MANAGER.clear();
        SERVER_FUTURE_MANAGER.clear();
        SERVER_NETWORK_MANAGER.registerPacketListener(SERVER_FUTURE_MANAGER);
        SERVER_FUTURE_MANAGER.registerPayloadHandler(ImagiphaseDowsingRodItem.class);
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

        if (worldDataSaveTask != null) {
            worldDataSaveTask.cancel(false);
        }
        worldDataSaveTask = AcademyCraft.executorService.scheduleAtFixedRate(
                WorldData::saveData, 5, 5, TimeUnit.MINUTES);
        AcademyCraft.LOGGER.info("Scheduled periodic world data saving.");
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if (worldDataSaveTask != null) {
            worldDataSaveTask.cancel(false);
        }
        AcademyCraft.LOGGER.info("Server stopping. Performing final data saves...");
        WorldData.saveData();
        serverConfig.save();
    }
}