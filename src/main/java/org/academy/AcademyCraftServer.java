package org.academy;

import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.academy.api.common.network.NetworkSystem;
import org.academy.api.common.network.future.FutureManager;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.network.NetworkManagerServer;
import org.academy.api.server.network.future.FutureManagerServer;
import org.academy.api.server.wireless.WirelessManager;
import org.academy.internal.common.world.item.ImagiphaseDowsingRodItem;
import org.academy.internal.server.config.AbilityConfig;
import org.academy.internal.server.config.GenericConfig;
import org.academy.internal.server.world.level.storage.WorldData;

import java.io.File;

@EventBusSubscriber(modid = AcademyCraft.MODID)
public final class AcademyCraftServer {
    public static AcademyCraftConfig serverConfig;
    public static WorldData worldData;
    public static AbilityConfig abilityConfig;
    public static GenericConfig genericConfig;
    public static File serverConfigFile;
    public static File worldDataFile;
    public static final NetworkSystem NETWORK_SYSTEM = new NetworkSystem();
    public static final FutureManager FUTURE_MANAGER = new FutureManager();
    public static final NetworkManagerServer SERVER_NETWORK_MANAGER = new NetworkManagerServer(NETWORK_SYSTEM);
    public static final FutureManagerServer SERVER_FUTURE_MANAGER = new FutureManagerServer(FUTURE_MANAGER);

    @SubscribeEvent
    public static void init(ServerStartedEvent event) {
        AcademyCraft.init();

        SERVER_NETWORK_MANAGER.clear();
        SERVER_FUTURE_MANAGER.clear();
        SERVER_NETWORK_MANAGER.registerPacketListener(SERVER_FUTURE_MANAGER);
        SERVER_FUTURE_MANAGER.registerPayloadHandler(ImagiphaseDowsingRodItem.class);
        serverConfigFile = new File(new File(""), "config" + File.separator + AcademyCraft.MOD_ID + "-server" + ".json");
        worldDataFile = event.getServer().getWorldPath(LevelResource.ROOT).resolve(AcademyCraft.MOD_ID + ".json").toFile();
        AcademyCraft.checkFile(serverConfigFile);
        AcademyCraft.checkFile(worldDataFile);

        serverConfig = new AcademyCraftConfig(serverConfigFile);

        AcademyCraftConfig.registerConfigActions(AbilityConfig.KEY, AbilityConfig.Action.INSTANCE);
        AcademyCraftConfig.registerConfigActions(GenericConfig.KEY, GenericConfig.Action.INSTANCE);
        abilityConfig = serverConfig.getConfig(AbilityConfig.KEY);
        genericConfig = serverConfig.getConfig(GenericConfig.KEY);

        worldData = WorldData.getWorldData(worldDataFile);
        AbilitySystemServer.init(event.getServer());
        WirelessManager.initServer();
    }
}