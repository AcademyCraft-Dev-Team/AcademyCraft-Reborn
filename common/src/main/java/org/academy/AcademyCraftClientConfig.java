package org.academy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import org.academy.api.client.config.ClientConfig;
import org.academy.api.client.input.InputSystem;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unchecked")
public class AcademyCraftClientConfig {
    @SerializedName("skills")
    private final Map<String, ClientConfig> skills = new HashMap<>();
    @SerializedName("key")
    private final Map<String, InputSystem.InputPair> key = new HashMap<>();

    public AcademyCraftClientConfig() {}

    public <T extends ClientConfig> T getSkillClientConfig(String skill, T defaultConfig) {
        if (!skills.containsKey(skill)) {
            setSkillClientConfig(skill, defaultConfig);
        }
        return (T) skills.get(skill);
    }

    public <T extends ClientConfig> void setSkillClientConfig(String skill, T newConfig) {
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
        save();
    }

    public static AcademyCraftClientConfig loadConfig(File file) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        if (!isValidClientConfig(file)) {
            return writeDefaultClientConfig(gson, file);
        }
        try (FileReader reader = new FileReader(file)) {
            AcademyCraftClientConfig loadedConfig = gson.fromJson(reader, AcademyCraftClientConfig.class);
            if (loadedConfig == null) {
                AcademyCraft.LOGGER.warn("Client config file {} was empty or malformed, writing default.", file.getAbsolutePath());
                return writeDefaultClientConfig(gson, file);
            }
            return loadedConfig;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read client config file: " + file.getAbsolutePath(), e);
        }
    }

    private static boolean isValidClientConfig(File file) {
        Gson gson = new GsonBuilder().create();
        try (FileReader fileReader = new FileReader(file)) {
            JsonObject jsonObject;
            try {
                jsonObject = gson.fromJson(fileReader, JsonObject.class);
            } catch (JsonSyntaxException e) {
                return false;
            }
            if (jsonObject == null) return false;
            return jsonObject.has("key") && jsonObject.has("skills");
        } catch (IOException e) {
            return false;
        }
    }

    private static AcademyCraftClientConfig writeDefaultClientConfig(Gson gson, File file) {
        AcademyCraftClientConfig defaultConfig = new AcademyCraftClientConfig();
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(defaultConfig, writer);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write default client config: " + file.getAbsolutePath(), e);
        }
        return defaultConfig;
    }

    public void save() {
        File configFile = AcademyCraftClient.CLIENT_CONFIG_FILE;
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(configFile)) {
            gson.toJson(this, writer);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to save client config file: " + configFile.getAbsolutePath(), e);
        }
    }
}