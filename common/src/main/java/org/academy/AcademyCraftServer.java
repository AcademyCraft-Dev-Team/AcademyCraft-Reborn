package org.academy;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.academy.api.common.command.CommandManager;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.world.level.block.entity.AbilityDeveloperBlockEntity;
import org.academy.internal.server.world.level.storage.AcademyCraftWorldData;

import java.io.File;

public class AcademyCraftServer {
    public static AcademyCraftServerConfig serverConfig;
    public static AcademyCraftWorldData academyCraftWorldData;
    public static File serverConfigFile;
    public static File worldDataFile;

    public static void init(final MinecraftServer server) {
        serverConfigFile = new File(server.getServerDirectory(), "config" + File.separator + AcademyCraft.MOD_ID + "-server" + ".json");
        worldDataFile = server.getWorldPath(LevelResource.ROOT).resolve(AcademyCraft.MOD_ID + ".json").toFile();
        AcademyCraft.checkFile(serverConfigFile);
        AcademyCraft.checkFile(worldDataFile);
        serverConfig = new AcademyCraftServerConfig().loadConfig(serverConfigFile);
        academyCraftWorldData = AcademyCraftWorldData.getWorldData(worldDataFile);
        AbilitySystemServer.init(server);
        CommandManager.Server.registerPacketHandler();
        AbilityDeveloperBlockEntity.intiServer();
    }
}