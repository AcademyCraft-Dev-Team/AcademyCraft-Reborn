package org.academy.api.client.config;

import com.google.gson.annotations.SerializedName;
import org.academy.api.client.input.InputSystem;

import java.util.HashMap;
import java.util.Map;

public abstract class KeyBindingConfig {
    @SerializedName("keyBindings")
    private final Map<String, InputSystem.KeyCombination> keyBindings = new HashMap<>();
    @SerializedName("enabledBindings")
    private final Map<String, Boolean> enabledBindings = new HashMap<>();

    public InputSystem.KeyCombination getKeyBinding(String name, InputSystem.KeyCombination defaultConfig) {
        if (!keyBindings.containsKey(name)) {
            setKeyBinding(name, defaultConfig);
        }
        return keyBindings.get(name);
    }

    public void setKeyBinding(String name, InputSystem.KeyCombination keyBinding) {
        keyBindings.put(name, keyBinding);
    }

    public Map<String, InputSystem.KeyCombination> getKeyBindings() {
        return keyBindings;
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