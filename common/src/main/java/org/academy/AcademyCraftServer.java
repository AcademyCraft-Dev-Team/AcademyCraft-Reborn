package org.academy;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.BusBuilderImpl;
import net.neoforged.bus.api.IEventBus;
import org.academy.api.common.network.NetworkSystem;
import org.academy.api.common.network.future.FutureManager;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.network.NetworkManagerServer;
import org.academy.api.server.network.future.FutureManagerServer;
import org.academy.api.server.wireless.WirelessManager;
import org.academy.internal.common.world.item.ImagPhaseDosingRodItem;
import org.academy.internal.server.config.AbilityConfig;
import org.academy.internal.server.config.GenericConfig;
import org.academy.internal.server.world.level.storage.WorldData;

import java.io.File;

public final class AcademyCraftServer {
    public static AcademyCraftConfig serverConfig;
    public static WorldData worldData;
    public static AbilityConfig abilityConfig;
    public static GenericConfig genericConfig;
    public static File serverConfigFile;
    public static File worldDataFile;
    public static final IEventBus EVENT_BUS = new BusBuilderImpl().build();
    public static final NetworkSystem NETWORK_SYSTEM_INSTANCE = new NetworkSystem();
    public static final FutureManager FUTURE_MANAGER_INSTANCE = new FutureManager();
    public static final NetworkManagerServer NETWORK_SYSTEM_SERVER_INSTANCE = new NetworkManagerServer(NETWORK_SYSTEM_INSTANCE);
    public static final FutureManagerServer FUTURE_MANAGER_SERVER_INSTANCE = new FutureManagerServer(FUTURE_MANAGER_INSTANCE);

    public static void init(final MinecraftServer server) {
        NETWORK_SYSTEM_SERVER_INSTANCE.clear();
        FUTURE_MANAGER_SERVER_INSTANCE.clear();
        NETWORK_SYSTEM_SERVER_INSTANCE.registerPacketListener(FUTURE_MANAGER_SERVER_INSTANCE);
        FUTURE_MANAGER_SERVER_INSTANCE.registerPayloadHandler(ImagPhaseDosingRodItem.class);
        serverConfigFile = new File(server.getServerDirectory(), "config" + File.separator + AcademyCraft.MOD_ID + "-server" + ".json");
        worldDataFile = server.getWorldPath(LevelResource.ROOT).resolve(AcademyCraft.MOD_ID + ".json").toFile();
        AcademyCraft.checkFile(serverConfigFile);
        AcademyCraft.checkFile(worldDataFile);

        serverConfig = new AcademyCraftConfig(serverConfigFile);

        AcademyCraftConfig.registerConfigActions(AbilityConfig.KEY, AbilityConfig.Action.INSTANCE);
        AcademyCraftConfig.registerConfigActions(GenericConfig.KEY, GenericConfig.Action.INSTANCE);
        abilityConfig = serverConfig.getConfig(AbilityConfig.KEY);
        genericConfig = serverConfig.getConfig(GenericConfig.KEY);

        worldData = WorldData.getWorldData(worldDataFile);
        AbilitySystemServer.init(server);
        WirelessManager.initServer();
    }
}