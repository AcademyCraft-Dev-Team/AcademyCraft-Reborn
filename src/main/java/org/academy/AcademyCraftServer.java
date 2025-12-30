package org.academy;

import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.api.server.wireless.WirelessManager;
import org.academy.internal.server.ability.PlayerDataManager;
import org.academy.internal.server.config.AbilityConfig;
import org.academy.internal.server.config.GenericConfig;
import org.academy.internal.server.world.level.storage.Player;
import org.academy.internal.server.world.level.storage.WorldData;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Logic server, not physical.
 */
@EventBusSubscriber
public final class AcademyCraftServer {
    private static final Logger LOGGER = AcademyCraft.getLogger();

    private final File serverConfigFile;
    private final File worldDataFile;

    private final AcademyCraftConfig serverConfig;
    private final PlayerDataManager playerDataManager;
    private final AbilityConfig abilityConfig;
    private final GenericConfig genericConfig;
    private final WorldData worldData;
    private final AbilitySystemServer abilitySystemServer;

    private final ScheduledFuture<?> worldDataSaveTask;

    private AcademyCraftServer(MinecraftServerContext context) {
        context.setAcademyCraftServer(this);
        var server = context.getMinecraftServer();

        serverConfigFile = new File(
                server.getServerDirectory().toFile(),
                "config" + File.separator + AcademyCraft.MOD_ID + "-server" + ".json"
        );
        worldDataFile = server.getWorldPath(
                LevelResource.ROOT).resolve(AcademyCraft.MOD_ID + ".json"
        ).toFile();
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

        abilitySystemServer = new AbilitySystemServer(context, playerDataManager, abilityConfig);
        WirelessManager.initServer();

        worldDataSaveTask = AcademyCraft.EXECUTOR_SERVICE.scheduleAtFixedRate(
                () -> {
                    abilitySystemServer.flushToData();
                    saveData();
                }, 5, 5, TimeUnit.MINUTES
        );
    }

    public AbilityConfig getAbilityConfig() {
        return abilityConfig;
    }

    public AbilitySystemServer getAbilitySystemServer() {
        return abilitySystemServer;
    }

    public void saveData() {
        var hasDirtyData = worldData.getPlayers().values().stream()
                .anyMatch(Player::isDirty);

        if (!hasDirtyData) return;

        LOGGER.debug("Dirty data detected, saving world data...");
        var gson = WorldData.createGson();

        try (var fileWriter = new FileWriter(worldDataFile)) {
            gson.toJson(worldData, fileWriter);
        } catch (IOException e) {
            LOGGER.error("Failed to save world data", e);
            return;
        }

        worldData.getPlayers().values().forEach(Player::clean);
        LOGGER.debug("World data saved and dirty flags cleaned.");
    }

    @SubscribeEvent
    public static void init(ServerStartedEvent event) {
        new AcademyCraftServer((MinecraftServerContext) event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        var context = (MinecraftServerContext) event.getServer();
        var instance = context.getAcademyCraftServer();
        instance.worldDataSaveTask.cancel(false);
        LOGGER.info("Server stopping. Performing final data saves...");
        instance.abilitySystemServer.onServerStopping();
        instance.saveData();
        instance.serverConfig.save();
    }
}