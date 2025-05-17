package org.academy.api.client.config;

import com.google.gson.annotations.SerializedName;
import org.academy.api.client.input.InputSystem;

import java.util.HashMap;
import java.util.Map;

public abstract class ClientConfig {
    protected ClientConfig() {
    }

    public static class KeyBindingConfig extends ClientConfig {
        @SerializedName("keyBindings")
        private final Map<String, InputSystem.InputPair> keyBindings = new HashMap<>();

        public InputSystem.InputPair getKeyBinding(String name, InputSystem.InputPair defaultConfig) {
            if (!keyBindings.containsKey(name)) {
                setKeyBinding(name, defaultConfig);
            }
            return keyBindings.get(name);
        }

        public InputSystem.InputPair getKeyBinding(String name) {
            return keyBindings.get(name);
        }

        public void setKeyBinding(String name, InputSystem.InputPair keyBinding) {
            this.keyBindings.put(name, keyBinding);
        }
    }
}