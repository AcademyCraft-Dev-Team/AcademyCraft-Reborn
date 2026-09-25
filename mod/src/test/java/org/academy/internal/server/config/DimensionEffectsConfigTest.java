package org.academy.internal.server.config;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DimensionEffectsConfigTest {
    @Test
    void createsDisabledFileWithFourIndependentEmptyLists(@TempDir Path directory) throws Exception {
        var file = directory.resolve("config").resolve(DimensionEffectsConfig.FILE_NAME);
        var config = DimensionEffectsConfig.load(file);
        assertFalse(config.enabled);
        assertFalse(DimensionEffectsConfig.load(file).enabled);
        assertTrue(config.pvp.whitelist.isEmpty());
        assertTrue(config.pvp.blacklist.isEmpty());
        assertTrue(config.blockDestruction.whitelist.isEmpty());
        assertTrue(config.blockDestruction.blacklist.isEmpty());
        var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals(Set.of("whitelist", "blacklist"), json.getAsJsonObject("pvp").keySet());
        assertEquals(Set.of("whitelist", "blacklist"), json.getAsJsonObject("blockDestruction").keySet());
    }

    @Test
    void readsSimultaneousListsWithoutRewritingAdministratorsFile(@TempDir Path directory) throws Exception {
        var file = directory.resolve(DimensionEffectsConfig.FILE_NAME);
        var json = """
                {
                  "enabled": true,
                  "pvp": {"whitelist": ["example:arena"], "blacklist": ["minecraft:overworld"]},
                  "blockDestruction": {"whitelist": ["minecraft:overworld"], "blacklist": ["example:arena"]}
                }
                """;
        Files.writeString(file, json);
        var config = DimensionEffectsConfig.load(file);
        assertTrue(config.enabled);
        assertEquals(Set.of("example:arena"), config.pvp.whitelist);
        assertEquals(Set.of("minecraft:overworld"), config.pvp.blacklist);
        assertEquals(Set.of("minecraft:overworld"), config.blockDestruction.whitelist);
        assertEquals(Set.of("example:arena"), config.blockDestruction.blacklist);
        assertEquals(json, Files.readString(file));
    }

    @Test
    void migratesLegacyModesToCorrespondingListsWithoutRewritingFile(@TempDir Path directory) throws Exception {
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
        assertEquals(Set.of("example:arena"), config.pvp.whitelist);
        assertTrue(config.pvp.blacklist.isEmpty());
        assertTrue(config.blockDestruction.whitelist.isEmpty());
        assertEquals(Set.of("minecraft:overworld"), config.blockDestruction.blacklist);
        assertEquals(json, Files.readString(file));
    }

    @Test
    void explicitNewListsReplaceStaleLegacyFields(@TempDir Path directory) throws Exception {
        var file = directory.resolve(DimensionEffectsConfig.FILE_NAME);
        Files.writeString(file, """
                {
                  "enabled": true,
                  "pvp": {"mode": "BLACKLIST", "dimensions": ["example:arena"], "whitelist": ["example:arena"]},
                  "blockDestruction": {"mode": "WHITELIST", "dimensions": ["example:arena"], "blacklist": []}
                }
                """);
        var config = DimensionEffectsConfig.load(file);
        assertEquals(Set.of("example:arena"), config.pvp.whitelist);
        assertTrue(config.pvp.blacklist.isEmpty());
        assertTrue(config.blockDestruction.whitelist.isEmpty());
        assertTrue(config.blockDestruction.blacklist.isEmpty());
    }

    @Test
    void invalidFilesFailVisiblyAndRemainIntact(@TempDir Path directory) throws Exception {
        var file = directory.resolve(DimensionEffectsConfig.FILE_NAME);
        for (var json : new String[]{
                "", "null", "{broken", "[]",
                "{\"enabled\":true,\"pvp\":null}",
                "{\"enabled\":true,\"pvp\":{\"mode\":\"WHITLIST\"}}",
                "{\"enabled\":true,\"pvp\":{\"dimensions\":null}}",
                "{\"enabled\":true,\"pvp\":{\"whitelist\":null}}",
                "{\"enabled\":true,\"blockDestruction\":{\"blacklist\":null}}",
                "{\"enabled\":true,\"pvp\":{\"whitelist\":[null]}}",
                "{\"enabled\":true,\"pvp\":{\"blacklist\":[\"overworld\"]}}",
                "{\"enabled\":true,\"blockDestruction\":{\"whitelist\":[\"Minecraft:Overworld\"]}}"
        }) {
            Files.writeString(file, json);
            var failure = assertThrows(IllegalStateException.class, () -> DimensionEffectsConfig.load(file), json);
            assertTrue(failure.getMessage().contains(file.toString()));
            assertEquals(json, Files.readString(file));
        }
    }
}
