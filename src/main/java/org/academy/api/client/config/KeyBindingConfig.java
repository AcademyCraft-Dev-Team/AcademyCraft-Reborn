package org.academy.api.client.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import org.academy.api.client.input.InputSystem;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

public abstract class KeyBindingConfig {
    private static final Gson GSON = new Gson();

    @SerializedName("keyBindings")
    private Map<String, JsonElement> keyBindings = new LinkedHashMap<>();
    @SerializedName("enabledBindings")
    private Map<String, Boolean> enabledBindings = new LinkedHashMap<>();

    public InputSystem.KeyCombination getKeyBinding(String name, InputSystem.KeyCombination defaultConfig) {
        InputSystem.rememberDefaultKeyBinding(name, defaultConfig);
        var keyBinding = decodeKeyBinding(keyBindingMap().get(name));
        if (!(keyBinding != null)) {
            setKeyBinding(name, defaultConfig);
            return defaultConfig;
        }
        migrateStoredKeyBinding(name, keyBinding);
        return keyBinding;
    }

    public InputSystem.@Nullable KeyCombination getKeyBinding(String name) {
        var keyBinding = decodeKeyBinding(keyBindingMap().get(name));
        if (!(keyBinding != null)) {
            return null;
        }
        migrateStoredKeyBinding(name, keyBinding);
        return keyBinding;
    }

    public boolean containsKeyBinding(String name) {
        return keyBindingMap().containsKey(name);
    }

    public Map<String, InputSystem.KeyCombination> getKeyBindings() {
        var result = new LinkedHashMap<String, InputSystem.KeyCombination>();
        for (var name : keyBindingMap().keySet()) {
            var keyBinding = getKeyBinding(name);
            if (keyBinding != null) {
                result.put(name, keyBinding);
            }
        }
        return Map.copyOf(result);
    }

    public void setKeyBinding(String name, InputSystem.KeyCombination keyBinding) {
        keyBindingMap().put(name, GSON.toJsonTree(keyBinding));
    }

    public boolean isKeyBindingEnabled(String name) {
        return enabledBindingMap().getOrDefault(name, true);
    }

    public void setKeyBindingEnabled(String name, boolean enabled) {
        enabledBindingMap().put(name, enabled);
    }

    private Map<String, JsonElement> keyBindingMap() {
        return keyBindings;
    }

    private Map<String, Boolean> enabledBindingMap() {
        return enabledBindings;
    }

    private void migrateStoredKeyBinding(String name, InputSystem.KeyCombination keyBinding) {
        var stored = keyBindingMap().get(name);
        if (!isCurrentKeyBinding(stored)) {
            setKeyBinding(name, keyBinding);
        }
    }

    private static boolean isCurrentKeyBinding(@Nullable JsonElement element) {
        if (element == null || !element.isJsonObject()) return false;
        var object = element.getAsJsonObject();
        return object.has("type") && object.has("keys");
    }

    private static InputSystem.@Nullable KeyCombination decodeKeyBinding(@Nullable JsonElement element) {
        if (element == null || !element.isJsonObject()) return null;
        try {
            if (isCurrentKeyBinding(element)) {
                return GSON.fromJson(element, InputSystem.KeyCombination.class);
            }
            return decodeLegacyKeyBinding(element.getAsJsonObject());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * Converts the pre-KeyCombination InputPair/KeyInfo representation.
     */
    private static InputSystem.@Nullable KeyCombination decodeLegacyKeyBinding(JsonObject object) {
        if (!object.has("inputType") || !object.has("keyInfo") || !object.get("keyInfo").isJsonObject()) {
            return null;
        }

        var type = InputSystem.InputType.valueOf(object.get("inputType").getAsString());
        var keyInfo = object.getAsJsonObject("keyInfo");
        if (!keyInfo.has("inputs") || !keyInfo.get("inputs").isJsonArray() || !keyInfo.has("action")) {
            return null;
        }

        var keys = new LinkedHashSet<Integer>();
        for (var key : keyInfo.getAsJsonArray("inputs")) {
            keys.add(key.getAsInt());
        }

        var modifiers = InputSystem.ANY_MODIFIER;
        if (keyInfo.has("modifiers") && keyInfo.get("modifiers").isJsonArray()) {
            modifiers = 0;
            for (var modifier : keyInfo.getAsJsonArray("modifiers")) {
                var value = modifier.getAsInt();
                if (value == InputSystem.ANY_MODIFIER) {
                    modifiers = InputSystem.ANY_MODIFIER;
                    break;
                }
                modifiers |= value;
            }
        }

        var availableWhenScreen = object.has("availableWhenScreen")
                && object.get("availableWhenScreen").getAsBoolean();
        return new InputSystem.KeyCombination(
                type,
                keys,
                keyInfo.get("action").getAsInt(),
                modifiers,
                availableWhenScreen,
                false
        );
    }
}
