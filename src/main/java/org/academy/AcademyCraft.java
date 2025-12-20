package org.academy;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.academy.internal.client.data.AcademyCraftClientData;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Mod(AcademyCraft.MODID)
public final class AcademyCraft {
    public static final String MODID = "academy";
    public static final ScheduledExecutorService EXECUTOR_SERVICE = Executors.newSingleThreadScheduledExecutor();
    public static final String MOD_ID = "academy";
    public static final String MOD_NAME = "AcademyCraft";
    public static boolean DEBUG_UI = false;
    private static final Logger LOGGER = LogUtils.getLogger();

    public AcademyCraft(IEventBus modEventBus) {
        AcademyCraftRegister.register(modEventBus);
        modEventBus.addListener(AcademyCraftClientData::dataSetup);
    }

    public static void checkFile(File file) {
        String errorMessage = null;

        try {
            if (file.exists()) {
                LOGGER.debug("File already exists: {}", file.getAbsolutePath());
                return;
            }

            var parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
                errorMessage = "Failed to create directories: " + parentDir.getAbsolutePath();
            } else if (!file.createNewFile()) {
                errorMessage = "Failed to create new file: " + file.getAbsolutePath();
            } else {
                LOGGER.debug("Successfully created new file: {}", file.getAbsolutePath());
            }
        } catch (IOException e) {
            errorMessage = "An error occurred while creating the file: " + file.getAbsolutePath() + " - " + e.getMessage();
        }

        if (errorMessage != null) {
            throw new RuntimeException(errorMessage);
        }
    }

    public static Identifier custom(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }

    public static Identifier vanilla(String name) {
        return Identifier.withDefaultNamespace(name);
    }

    public static Identifier academy(String name) {
        return Identifier.fromNamespaceAndPath(MOD_ID, name);
    }
}