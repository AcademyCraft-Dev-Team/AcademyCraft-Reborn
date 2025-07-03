package org.academy;

import com.google.gson.*;
import org.academy.api.common.config.IConfigAction;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AcademyCraftConfig {
    private final File configFile;
    private JsonObject rootJsonConfig;

    private final Map<String, Object> runtimeConfigCache = new ConcurrentHashMap<>();
    private static final Map<String, IConfigAction<?>> CONFIG_ACTIONS_MAP = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().create();

    public AcademyCraftConfig(@NotNull File configFile) {
        this.configFile = configFile;
        load();
    }

    public static void registerConfigActions(@NotNull String configKey, @NotNull IConfigAction<?> actions) {
        CONFIG_ACTIONS_MAP.put(configKey, actions);
    }

    private void load() {
        if (configFile.exists() && configFile.length() > 0) {
            try (var reader = new FileReader(configFile)) {
                var parsedElement = JsonParser.parseReader(reader);
                if (parsedElement.isJsonObject()) {
                    rootJsonConfig = parsedElement.getAsJsonObject();
                } else {
                    rootJsonConfig = new JsonObject();
                }
            } catch (IOException | JsonSyntaxException e) {
                rootJsonConfig = new JsonObject();
            }
        } else {
            rootJsonConfig = new JsonObject();
        }
    }

    public void save() {
        try (var writer = new FileWriter(configFile)) {
            rootJsonConfig = new JsonObject();
            for (var cacheEntry : runtimeConfigCache.entrySet()) {
                var configKey = cacheEntry.getKey();
                var configInstance = cacheEntry.getValue();
                var actions = CONFIG_ACTIONS_MAP.get(configKey);
                rootJsonConfig.add(configKey, actions.serializeRaw(configInstance, GSON));
            }
            var gsonPretty = new GsonBuilder().setPrettyPrinting().create();
            gsonPretty.toJson(rootJsonConfig, writer);
        } catch (Throwable e) {
            AcademyCraft.LOGGER.warn("Failed to save config to {}", configFile.getAbsolutePath(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T getConfig(@NotNull String configKey) {
        if (runtimeConfigCache.containsKey(configKey)) {
            return (T) runtimeConfigCache.get(configKey);
        }

        var jsonElement = rootJsonConfig.get(configKey);
        T configInstance;
        var action = (IConfigAction<T>) CONFIG_ACTIONS_MAP.get(configKey);
        if (action == null) {
            throw new RuntimeException("No config action registered for key: " + configKey + " . Returning null.");
        }
        if (jsonElement == null) {
            var defaultConfig = action.getDefaultConfig();
            runtimeConfigCache.put(configKey, defaultConfig);
            configInstance = defaultConfig;
            save();
        } else {
            configInstance = action.deserialize(jsonElement, GSON);
            runtimeConfigCache.put(configKey, configInstance);
        }
        return configInstance;
    }

    public void setConfig(@NotNull String configKey, @NotNull Object configInstance) {
        var actions = CONFIG_ACTIONS_MAP.get(configKey);
        if (actions == null) {
            AcademyCraft.LOGGER.warn("Attempted to set config for unregistered key: {}", configKey);
            return;
        }
        if (!actions.getConfigClass().isInstance(configInstance)) {
            AcademyCraft.LOGGER.warn("Attempted to set config with incorrect type for key: {}. Expected {}, got {}",
                    configKey, actions.getConfigClass().getName(), configInstance.getClass().getName());
            return;
        }

        runtimeConfigCache.put(configKey, configInstance);
        rootJsonConfig.add(configKey, actions.serializeRaw(configInstance, GSON));
        save();
    }
}