package org.academy.api.client.config;

import com.google.gson.annotations.SerializedName;
import org.academy.AcademyCraft;
import org.academy.api.client.input.InputSystem;

import java.util.HashMap;
import java.util.Map;

public class AcademyCraftClientConfig<SC extends SkillClientConfig> {
    @SerializedName("skills")
    private final Map<String, SC> skills = new HashMap<>();
    @SerializedName("key")
    private final Map<String, InputSystem.InputPair> key = new HashMap<>();

    public SC getSkillClientConfig(String skill, SC defaultConfig) {
        if (!skills.containsKey(skill)) {
            setSkillClientConfig(skill, defaultConfig);
        }
        return skills.get(skill);
    }

    public void setSkillClientConfig(String skill, SC newConfig) {
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