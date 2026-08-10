package org.academy;

import com.google.gson.JsonParser;
import org.academy.api.common.gson.TypeHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcademyCraftConfigMutationSaveTest {
    private static final String CONFIG_KEY = "test.key_binding_migration_persistence";

    @Test
    void savesNestedMutationsAndPreservesUnloadedEntries(@TempDir Path tempDir) throws IOException {
        var file = tempDir.resolve("academy-client.json");
        Files.writeString(file, """
                {
                  "test.key_binding_migration_persistence": {"value": "before"},
                  "unloaded.config": {"keep": true}
                }
                """);
        AcademyCraftConfig.registerTypeHandler(CONFIG_KEY, MutableConfig.Action.INSTANCE);
        var configFile = new AcademyCraftConfig(file.toFile());
        var mutableConfig = configFile.<MutableConfig>getConfig(CONFIG_KEY);

        mutableConfig.value = "after";
        configFile.save();

        var saved = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals("after", saved.getAsJsonObject(CONFIG_KEY).get("value").getAsString());
        assertTrue(saved.getAsJsonObject("unloaded.config").get("keep").getAsBoolean());
    }

    private static final class MutableConfig {
        private String value = "default";

        private static final class Action implements TypeHandler<MutableConfig> {
            private static final Action INSTANCE = new Action();

            @Override
            public MutableConfig getDefault() {
                return new MutableConfig();
            }

            @Override
            public Class<MutableConfig> getTypeClass() {
                return MutableConfig.class;
            }
        }
    }
}
