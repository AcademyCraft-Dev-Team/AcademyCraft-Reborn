package org.academy;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.BusBuilderImpl;
import net.neoforged.bus.api.IEventBus;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.network.NetworkSystemServer;
import org.academy.api.server.wireless.WirelessManager;
import org.academy.internal.server.world.level.storage.WorldData;

import java.io.File;

public class AcademyCraftServer {
    public static AcademyCraftServerConfig serverConfig;
    public static WorldData worldData;
    public static File serverConfigFile;
    public static File worldDataFile;
    public static final IEventBus EVENT_BUS = new BusBuilderImpl().build();

    public static void init(final MinecraftServer server) {
        serverConfigFile = new File(server.getServerDirectory(), "config" + File.separator + AcademyCraft.MOD_ID + "-server" + ".json");
        worldDataFile = server.getWorldPath(LevelResource.ROOT).resolve(AcademyCraft.MOD_ID + ".json").toFile();
        AcademyCraft.checkFile(serverConfigFile);
        AcademyCraft.checkFile(worldDataFile);
        serverConfig = new AcademyCraftServerConfig().loadConfig(serverConfigFile);
        worldData = WorldData.getWorldData(worldDataFile);
        AbilitySystemServer.init(server);
        WirelessManager.initServer();
        NetworkSystemServer.init();
    }
}