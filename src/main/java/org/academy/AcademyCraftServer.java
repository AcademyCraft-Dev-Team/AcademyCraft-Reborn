package org.academy;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.academy.api.common.network.NetworkManager;
import org.academy.api.common.network.packet.Packet;
import org.academy.api.common.network.packet.S2CPacket;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.network.future.FutureManagerServer;
import org.academy.api.server.wireless.WirelessManager;
import org.academy.internal.common.world.item.ImagiphaseDowsingRodItem;
import org.academy.internal.server.ability.PlayerDataManager;
import org.academy.internal.server.config.AbilityConfig;
import org.academy.internal.server.config.GenericConfig;
import org.academy.internal.server.world.level.storage.WorldData;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Logic server, not physical.
 */
@EventBusSubscriber(modid = AcademyCraft.MODID)
public final class AcademyCraftServer {
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
    public static final NetworkManager NETWORK_MANAGER = new NetworkManager();
    public static final FutureManagerServer FUTURE_MANAGER = new FutureManagerServer();

    @Nullable
    private static ScheduledFuture<?> worldDataSaveTask;

    private AcademyCraftServer() {
    }

    @SubscribeEvent
    public static void init(ServerStartedEvent event) {
        NETWORK_MANAGER.clear();
        FUTURE_MANAGER.clear();
        NETWORK_MANAGER.registerPacketListener(FUTURE_MANAGER);
        FUTURE_MANAGER.registerPayloadHandler(ImagiphaseDowsingRodItem.class);
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
        if (serverConfig != null) {
            serverConfig.save();
        }
    }

    public static <P extends Packet<ClientGamePacketListener, P>> void sendPacket(ServerPlayer player, P packet) {
        player.connection.send(new S2CPacket(packet));
    }

    public static <P extends Packet<ClientGamePacketListener, P>> void sendPacket(Connection connection, P packet) {
        connection.send(new S2CPacket(packet));
    }

    public static <P extends Packet<ClientGamePacketListener, P>> void sendPacket(ServerGamePacketListenerImpl listener, P packet) {
        listener.send(new S2CPacket(packet));
    }
}