package org.academy.api.client.config;

import com.google.gson.annotations.SerializedName;
import org.academy.AcademyCraft;
import org.academy.api.client.input.InputSystem;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unchecked")
public class AcademyCraftClientConfig {
    @SerializedName("skills")
    private final Map<String, SkillClientConfig> skills = new HashMap<>();
    @SerializedName("key")
    private final Map<String, InputSystem.InputPair> key = new HashMap<>();

    public <T extends SkillClientConfig> T getSkillClientConfig(String skill, T defaultConfig) {
        if (!skills.containsKey(skill)) {
            setSkillClientConfig(skill, defaultConfig);
        }
        return (T) skills.get(skill);
    }

    public <T extends SkillClientConfig> void setSkillClientConfig(String skill, T newConfig) {
        skills.put(skill, newConfig);
    }

    public InputSystem.InputPair getKey(String name, InputSystem.InputPair defaultValue) {
        if (!key.containsKey(name)) {
            setKey(name, defaultValue);
        }
        return key.get(name);
    }

    public void setKey(String name, InputSystem.InputPair value) {
        key.put(name, value);
        AcademyCraft.saveConfig();
    }
}