package org.academy.internal.server.config;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GenericConfigTest {
    @Test
    void defaultsEnableGameplayAndCarryTheFriendlyFireList() {
        var config = GenericConfig.Action.INSTANCE.getDefault();

        assertTrue(config.general.blockDestruction);
        assertTrue(config.general.pvp);
        assertFalse(config.general.devMode);
        assertTrue(config.worldgen.generateImagPhaseLakes);
        assertEquals(2, config.friendlyFire.ctaWhitelist.size());
        assertTrue(config.friendlyFire.ctaWhitelist.contains("tamed"));
    }

    @Test
    void legacyBooleanMapMigratesIntoTypedSettings() {
        var json = """
                {
                  "booleanMap": {
                    "destroyBlocks": false,
                    "attackPlayer": false,
                    "devMode": true,
                    "genPhaseLiquid": false
                  },
                  "stringListMap": {
                    "ctaFriendlyFireWhitelist": ["tamed", "example:npc"]
                  }
                }
                """;
        var adapter = GenericConfig.Action.INSTANCE.getAdapter(new GsonBuilder().create());
        var config = adapter.fromJsonTree(JsonParser.parseString(json));

        assertFalse(config.general.blockDestruction);
        assertFalse(config.general.pvp);
        assertTrue(config.general.devMode);
        assertFalse(config.worldgen.generateImagPhaseLakes);
        assertEquals(2, config.friendlyFire.ctaWhitelist.size());
        assertTrue(config.friendlyFire.ctaWhitelist.contains("example:npc"));
    }

    @Test
    void typedValuesWinOverLegacyMaps() {
        var json = """
                {
                  "general": { "blockDestruction": true },
                  "booleanMap": { "destroyBlocks": false }
                }
                """;
        var adapter = GenericConfig.Action.INSTANCE.getAdapter(new GsonBuilder().create());
        var config = adapter.fromJsonTree(JsonParser.parseString(json));

        // Legacy migration only fills a typed key the file does not define.
        assertTrue(config.general.blockDestruction);
    }

    @Test
    void serializedDefaultsUseTypedSectionsOnly() {
        var config = GenericConfig.Action.INSTANCE.getDefault();
        var json = new GsonBuilder().create().toJson(config);

        assertTrue(json.contains("\"general\""));
        assertTrue(json.contains("\"worldgen\""));
        assertTrue(json.contains("\"friendlyFire\""));
        assertFalse(json.contains("booleanMap"));
        assertFalse(json.contains("stringListMap"));
    }
}
