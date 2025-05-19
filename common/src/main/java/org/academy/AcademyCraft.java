package org.academy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.bus.BusBuilderImpl;
import net.neoforged.bus.api.IEventBus;
import org.academy.api.common.network.NetworkSystem;
import org.academy.api.common.network.Packets;
import org.academy.api.common.util.GameUtil;
import org.academy.api.common.vanilla.ThreadType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public final class AcademyCraft {
    public static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    public static final boolean DEBUG_MODE = true;
    public static final String MOD_ID = "academy";
    public static final String MOD_NAME = "AcademyCraft";
    public static final Logger LOGGER = LogManager.getLogger(MOD_NAME);
    public static final IEventBus EVENT_BUS = new BusBuilderImpl().build();

    public static void init() {
        Packets.init();
        NetworkSystem.init();
    }

    public static void checkFile(File file) {
        String errorMessage = null;

        try {
            if (file.exists()) {
                AcademyCraft.LOGGER.debug("File already exists: {}", file.getAbsolutePath());
                return;
            }

            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
                errorMessage = "Failed to create directories: " + parentDir.getAbsolutePath();
            } else if (!file.createNewFile()) {
                errorMessage = "Failed to create new file: " + file.getAbsolutePath();
            } else {
                AcademyCraft.LOGGER.debug("Successfully created new file: {}", file.getAbsolutePath());
            }
        } catch (IOException e) {
            errorMessage = "An error occurred while creating the file: " + file.getAbsolutePath() + " - " + e.getMessage();
        }

        if (errorMessage != null) {
            throw new RuntimeException(errorMessage);
        }
    }

    public static void saveConfig() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        File configFile = GameUtil.getThreadType() == ThreadType.CLIENT ? AcademyCraftClient.CLIENT_CONFIG_FILE : AcademyCraftServer.serverConfigFile;

        try (FileWriter writer = new FileWriter(configFile)) {
            gson.toJson(GameUtil.getThreadType() == ThreadType.CLIENT ? AcademyCraftClient.CLIENT_CONFIG : AcademyCraftServer.serverConfig, writer);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to save config file: " + configFile.getAbsolutePath(), e);
        }
    }
}