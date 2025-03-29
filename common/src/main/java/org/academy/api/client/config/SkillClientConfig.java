package org.academy.api.client.config;

import com.google.gson.annotations.SerializedName;
import org.academy.api.client.input.InputSystem;

import java.util.HashMap;
import java.util.Map;

public abstract class SkillClientConfig {
    protected SkillClientConfig() {
    }

    public static class SkillClientKeyBindingConfig extends SkillClientConfig {
        @SerializedName("keyBindings")
        private final Map<String, InputSystem.InputPair> keyBindings = new HashMap<>();

        public InputSystem.InputPair getKeyBinding(String name, InputSystem.InputPair... defaultConfig) {
            if (!keyBindings.containsKey(name) && defaultConfig.length > 0) {
                setKeyBinding(name, defaultConfig[0]);
            }
            return keyBindings.get(name);
        }

        public void setKeyBinding(String name, InputSystem.InputPair keyBinding) {
            this.keyBindings.put(name, keyBinding);
        }
    }
}