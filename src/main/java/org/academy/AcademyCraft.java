package org.academy;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.academy.internal.client.data.AcademyCraftData;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Mod(AcademyCraft.MODID)
public final class AcademyCraft {
    public static final String MODID = "academy";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    public static final String MOD_ID = "academy";
    public static final String MOD_NAME = "AcademyCraft";
    public static boolean DEBUG_UI = false;

    public AcademyCraft(IEventBus modEventBus) {
        AcademyCraftRegister.register(modEventBus);
        modEventBus.addListener(AcademyCraftData::dataSetup);
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

    public static ResourceLocation custom(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    public static ResourceLocation vanilla(String name) {
        return ResourceLocation.withDefaultNamespace(name);
    }

    public static ResourceLocation academy(String name) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, name);
    }
}