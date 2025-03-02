package org.academy;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.academy.api.common.network.AcademyCraftNetworkSystem;
import org.academy.internal.AcademyCraftRegister;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public final class AcademyCraft implements ModInitializer {
    public static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    public static final boolean DEBUG_MODE = true;
    public static final String MOD_ID = "academy";
    public static final String MOD_NAME = "AcademyCraft";
    public static final Logger LOGGER = LogManager.getLogger("AcademyCraft");

    @Override
    public void onInitialize() {
        AcademyCraftRegister.init();
        AcademyCraftNetworkSystem.init();
        ServerLifecycleEvents.SERVER_STARTING.register(AcademyCraftServer::init);
        AbilitySystem.init();
    }

    public static void checkFile(File file) {
        String errorMessage = null;

        try {
            if (file.exists()) {
                AcademyCraft.LOGGER.info("File already exists: {}", file.getAbsolutePath());
                return;
            }

            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
                errorMessage = "Failed to create directories: " + parentDir.getAbsolutePath();
            } else if (!file.createNewFile()) {
                errorMessage = "Failed to create new file: " + file.getAbsolutePath();
            } else {
                AcademyCraft.LOGGER.info("Successfully created new file: {}", file.getAbsolutePath());
            }
        } catch (IOException e) {
            errorMessage = "An error occurred while creating the file: " + file.getAbsolutePath() + " - " + e.getMessage();
        }

        if (errorMessage != null) {
            throw new RuntimeException(errorMessage);
        }
    }

    public static void debugLog(Object message) {
        if (DEBUG_MODE) {
            AcademyCraft.LOGGER.debug(message);
        }
    }
}