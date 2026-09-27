package org.academy;

import net.minecraft.server.MinecraftServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.server.config.GenericConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class AcademyCraftWorldgenConfigTest {
    @Test
    void workersReadDisabledConfigBeforeGameplayRuntimeExists(@TempDir Path directory) throws Exception {
        writeConfig(directory, """
                {"generic":{"worldgen":{"generateImagPhaseLakes":false}}}
                """);
        var config = AcademyCraftServer.loadServerConfig(directory);
        var context = new BeforeServerStarted(config);
        var settings = config.<GenericConfig>getConfig(GenericConfig.KEY);
        try (var workers = Executors.newFixedThreadPool(8)) {
            var reads = new ArrayList<Callable<Boolean>>();
            for (var i = 0; i < 256; i++) {
                reads.add(() -> {
                    assertSame(settings, context.getAcademyCraftServerConfig().getConfig(GenericConfig.KEY));
                    return AcademyCraftServer.isImagPhaseGenerationEnabledForServer(context);
                });
            }
            for (var result : workers.invokeAll(reads, 10, TimeUnit.SECONDS)) {
                assertFalse(result.get());
            }
        }
        // Runtime and worldgen must keep using the same loaded settings, with no second disk read.
        Files.writeString(directory.resolve("config/academy-server.json"), "{}");
        assertFalse(AcademyCraftServer.isImagPhaseGenerationEnabledForServer(context));
        settings.worldgen.generateImagPhaseLakes = true;
        assertTrue(AcademyCraftServer.isImagPhaseGenerationEnabledForServer(context));
    }

    @Test
    void legacyDisableAndSeparateServerDefaultsWorkBeforeStartup(@TempDir Path directory) throws Exception {
        var disabled = directory.resolve("disabled");
        writeConfig(disabled, """
                {"generic":{"booleanMap":{"genPhaseLiquid":false}}}
                """);
        var disabledContext = new BeforeServerStarted(AcademyCraftServer.loadServerConfig(disabled));
        var defaultContext = new BeforeServerStarted(AcademyCraftServer.loadServerConfig(directory.resolve("default")));
        assertFalse(AcademyCraftServer.isImagPhaseGenerationEnabledForServer(disabledContext));
        assertTrue(AcademyCraftServer.isImagPhaseGenerationEnabledForServer(defaultContext));
        assertFalse(AcademyCraftServer.isImagPhaseGenerationEnabledForServer(disabledContext));
    }

    private static void writeConfig(Path directory, String json) throws Exception {
        Files.createDirectories(directory.resolve("config"));
        Files.writeString(directory.resolve("config/academy-server.json"), json);
    }

    private record BeforeServerStarted(AcademyCraftConfig config) implements MinecraftServerContext {
        @Override
        public AcademyCraftConfig getAcademyCraftServerConfig() {
            return config;
        }

        @Override
        public boolean hasAcademyCraftServer() {
            return false;
        }

        @Override
        public AcademyCraftServer getAcademyCraftServer() {
            throw new IllegalStateException("AcademyCraftServer has not been initialized.");
        }

        @Override
        public void setAcademyCraftServer(AcademyCraftServer server) {
            fail("World generation must not construct the gameplay runtime");
        }

        @Override
        public MinecraftServer getMinecraftServer() {
            throw new AssertionError("World generation must only read the loaded config");
        }
    }
}
