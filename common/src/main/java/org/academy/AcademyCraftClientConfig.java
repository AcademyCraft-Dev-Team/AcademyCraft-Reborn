package org.academy;

import com.google.gson.annotations.SerializedName;
import org.academy.api.client.input.InputSystem;

import java.util.HashMap;
import java.util.Map;

public class AcademyCraftClientConfig extends AcademyCraftConfig<AcademyCraftClientConfig> {
    @SerializedName("key")
    private final Map<String, InputPair> key = new HashMap<>();

    public InputPair getKey(String name, InputPair defaultValue) {
        if (!key.containsKey(name)) {
            setKey(name, defaultValue);
        }
        return key.get(name);
    }

    public void setKey(String name, InputPair value) {
        key.put(name, value);
        saveConfig();
    }

    public record InputPair(InputType inputType, InputSystem.InputEvent inputEvent) {
    }

    public enum InputType {
        MOUSE,
        KEYBOARD
    }

    @Override
    protected void writeDefaultConfig(AcademyCraftClientConfig academyCraftConfig) {
        Generic generic = academyCraftConfig.getGeneric();
        generic.getBooleanMap().put("useMouseWheel", true);
    }
}
