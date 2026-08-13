package org.academy.api.client.config;

import com.google.gson.Gson;
import com.mojang.blaze3d.platform.InputConstants;
import org.academy.api.client.input.InputSystem;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class KeyBindingConfigMigrationTest {
    private static final Gson GSON = new Gson();
    private static final String NAME = "legacy_test_cast";

    @Test
    void migratesLegacyInputPairAndPreservesCustomBinding() {
        var config = GSON.fromJson("""
                {
                  "keyBindings": {
                    "legacy_test_cast": {
                      "inputType": "MOUSE",
                      "keyInfo": {
                        "inputs": [1],
                        "action": 1,
                        "modifiers": [1, 4]
                      },
                      "availableWhenScreen": true
                    }
                  }
                }
                """, TestConfig.class);

        var binding = config.getKeyBinding(NAME, defaultBinding());

        assertEquals(InputSystem.InputType.MOUSE, binding.type());
        assertEquals(Set.of(1), binding.keys());
        assertEquals(InputConstants.PRESS, binding.action());
        assertEquals(InputConstants.MOD_SHIFT | InputConstants.MOD_ALT, binding.modifiers());
        assertTrue(binding.availableWhenScreen());

        var migrated = GSON.toJsonTree(config).getAsJsonObject()
                .getAsJsonObject("keyBindings").getAsJsonObject(NAME);
        assertNotNull(migrated.get("type"));
        assertNotNull(migrated.get("keys"));
        assertFalse(migrated.has("inputType"));
        assertFalse(migrated.has("keyInfo"));
    }

    @Test
    void replacesCurrentBindingWithMissingKeysWithDefault() {
        var config = GSON.fromJson("""
                {
                  "keyBindings": {
                    "legacy_test_cast": {
                      "type": "KEYBOARD",
                      "keys": null,
                      "action": 1,
                      "modifiers": 0,
                      "availableWhenScreen": false,
                      "unbound": false
                    }
                  }
                }
                """, TestConfig.class);

        assertEquals(defaultBinding(), config.getKeyBinding(NAME, defaultBinding()));
    }

    @Test
    void retainsValidCurrentBinding() {
        var config = GSON.fromJson("""
                {
                  "keyBindings": {
                    "legacy_test_cast": {
                      "type": "KEYBOARD",
                      "keys": [82],
                      "action": 0,
                      "modifiers": 2,
                      "availableWhenScreen": false,
                      "unbound": false
                    }
                  }
                }
                """, TestConfig.class);

        var binding = config.getKeyBinding(NAME, defaultBinding());

        assertEquals(Set.of(82), binding.keys());
        assertEquals(InputConstants.RELEASE, binding.action());
        assertEquals(InputConstants.MOD_CONTROL, binding.modifiers());
    }

    private static InputSystem.KeyCombination defaultBinding() {
        return InputSystem.combo(
                InputSystem.InputType.KEYBOARD,
                InputConstants.KEY_Y,
                InputConstants.PRESS,
                0
        );
    }

    private static final class TestConfig extends KeyBindingConfig {
    }
}
