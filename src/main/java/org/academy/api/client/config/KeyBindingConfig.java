package org.academy.api.client.config;

import com.google.gson.annotations.SerializedName;
import org.academy.api.client.input.InputSystem;

import java.util.LinkedHashMap;
import java.util.Map;

public abstract class KeyBindingConfig {
    @SerializedName("keyBindings")
    private final Map<String, InputSystem.KeyCombination> keyBindings = new LinkedHashMap<>();
    @SerializedName("enabledBindings")
    private final Map<String, Boolean> enabledBindings = new LinkedHashMap<>();

    public InputSystem.KeyCombination getKeyBinding(String name, InputSystem.KeyCombination defaultConfig) {
        InputSystem.rememberDefaultKeyBinding(name, defaultConfig);
        if (!keyBindings.containsKey(name)) {
            setKeyBinding(name, defaultConfig);
        }
        return keyBindings.get(name);
    }

    public InputSystem.KeyCombination getKeyBinding(String name) {
        return keyBindings.get(name);
    }

    public boolean containsKeyBinding(String name) {
        return keyBindings.containsKey(name);
    }

    public Map<String, InputSystem.KeyCombination> getKeyBindings() {
        return Map.copyOf(keyBindings);
    }

    public void setKeyBinding(String name, InputSystem.KeyCombination keyBinding) {
        keyBindings.put(name, keyBinding);
    }

    public boolean isKeyBindingEnabled(String name) {
        return enabledBindings.getOrDefault(name, true);
    }

    public void setKeyBindingEnabled(String name, boolean enabled) {
        if (enabled) {
            enabledBindings.put(name, true);
        } else {
            enabledBindings.put(name, false);
        }
    }
}
