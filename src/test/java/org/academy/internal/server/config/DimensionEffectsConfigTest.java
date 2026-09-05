package org.academy.internal.server.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DimensionEffectsConfigTest {
    @Test
    void createsDisabledFileAndReloadsIt(@TempDir Path directory) throws Exception {
        var file = directory.resolve("config").resolve(DimensionEffectsConfig.FILE_NAME);
        var config = DimensionEffectsConfig.load(file);
        assertFalse(config.enabled);
        assertTrue(Files.exists(file));
        assertFalse(DimensionEffectsConfig.load(file).enabled);
        assertEquals(DimensionEffectsConfig.Mode.BLACKLIST, config.pvp.mode);
        assertTrue(config.pvp.dimensions.isEmpty());
        assertTrue(config.blockDestruction.dimensions.isEmpty());
    }

    @Test
    void readsIndependentRulesWithoutRewritingAdministratorsFile(@TempDir Path directory) throws Exception {
        var file = directory.resolve(DimensionEffectsConfig.FILE_NAME);
        var json = """
                {
                  "enabled": true,
                  "pvp": {"mode": "WHITELIST", "dimensions": ["example:arena"]},
                  "blockDestruction": {"mode": "BLACKLIST", "dimensions": ["minecraft:overworld"]}
                }
                """;
        Files.writeString(file, json);
        var config = DimensionEffectsConfig.load(file);
        assertTrue(config.enabled);
        assertTrue(config.pvp.allows("example:arena"));
        assertFalse(config.pvp.allows("minecraft:overworld"));
        assertFalse(config.blockDestruction.allows("minecraft:overworld"));
        assertTrue(config.blockDestruction.allows("example:arena"));
        assertEquals(json, Files.readString(file));
    }

    @Test
    void invalidFilesFailVisiblyAndRemainIntact(@TempDir Path directory) throws Exception {
        var file = directory.resolve(DimensionEffectsConfig.FILE_NAME);
        for (var json : new String[]{
                "", "null", "{broken", "[]",
                "{\"enabled\":true,\"pvp\":null}",
                "{\"enabled\":true,\"pvp\":{\"mode\":\"WHITLIST\"}}",
                "{\"enabled\":true,\"pvp\":{\"dimensions\":null}}",
                "{\"enabled\":true,\"pvp\":{\"dimensions\":[null]}}",
                "{\"enabled\":true,\"pvp\":{\"dimensions\":[\"overworld\"]}}",
                "{\"enabled\":true,\"pvp\":{\"dimensions\":[\"Minecraft:Overworld\"]}}"
        }) {
            Files.writeString(file, json);
            var failure = assertThrows(IllegalStateException.class, () -> DimensionEffectsConfig.load(file), json);
            assertTrue(failure.getMessage().contains(file.toString()));
            assertEquals(json, Files.readString(file));
        }
    }
}
