package org.academy;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.IEventBus;
import org.academy.api.common.ability.AbilitySystem;
import org.academy.api.common.network.NetworkSystem;
import org.academy.api.common.util.GameUtil;
import org.academy.api.common.vanilla.ThreadType;
import org.academy.internal.common.network.Packets;
import org.academy.internal.common.network.future.Payloads;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public final class AcademyCraft {
    public static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    public static final String MOD_ID = "academy";
    public static final String MOD_NAME = "AcademyCraft";
    public static final Logger LOGGER = LogManager.getLogger(MOD_NAME);
    public static final IEventBus EVENT_BUS = BusBuilder.builder().build();
    public static boolean DEBUG_UI = false;

    public static void init() {
        NetworkSystem.registerVanillaPacketsOnce();
        var threadType = GameUtil.getThreadType();
        var networkSystem = threadType == ThreadType.CLIENT ?
                AcademyCraftClient.NETWORK_SYSTEM : AcademyCraftServer.NETWORK_SYSTEM;
        var futureManager = threadType == ThreadType.CLIENT ?
                AcademyCraftClient.FUTURE_MANAGER : AcademyCraftServer.FUTURE_MANAGER;
        networkSystem.clear();
        Packets.registerAll(networkSystem);
        AbilitySystem.init();
        Payloads.registerAll(futureManager);
    }

    public static void checkFile(File file) {
        String errorMessage = null;

        try {
            if (file.exists()) {
                AcademyCraft.LOGGER.debug("File already exists: {}", file.getAbsolutePath());
                return;
            }

            var parentDir = file.getParentFile();
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

    public static ResourceLocation getResourceLocation(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    public static ResourceLocation getResourceLocation(String namespace, String path) {
        return new ResourceLocation(namespace, path);
    }
}