package org.academy.api.client.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import org.academy.api.client.input.InputSystem;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

public abstract class KeyBindingConfig {
    private static final Gson GSON = new Gson();

    @SerializedName("keyBindings")
    private final Map<String, JsonElement> keyBindings = new LinkedHashMap<>();
    @SerializedName("enabledBindings")
    private final Map<String, Boolean> enabledBindings = new LinkedHashMap<>();

    public InputSystem.KeyCombination getKeyBinding(String name, InputSystem.KeyCombination defaultConfig) {
        InputSystem.rememberDefaultKeyBinding(name, defaultConfig);
        var keyBinding = decodeKeyBinding(keyBindingMap().get(name));
        if (keyBinding == null) {
            setKeyBinding(name, defaultConfig);
            return defaultConfig;
        }
        return keyBinding;
    }

    public InputSystem.@Nullable KeyCombination getKeyBinding(String name) {
        return decodeKeyBinding(keyBindingMap().get(name));
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

    public void removeKeyBinding(String name) {
        keyBindingMap().remove(name);
        enabledBindingMap().remove(name);
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

    private static InputSystem.@Nullable KeyCombination decodeKeyBinding(@Nullable JsonElement element) {
        if (element == null || !element.isJsonObject()) return null;
        try {
            return GSON.fromJson(element, InputSystem.KeyCombination.class);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
