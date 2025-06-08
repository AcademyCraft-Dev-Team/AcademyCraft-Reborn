package org.academy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.academy.api.client.config.IClientConfigActions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class AcademyCraftClientConfig {
    private final File configFile;
    private JsonObject rootJsonConfig;

    private final Map<String, Object> runtimeConfigCache = new HashMap<>();
    private static final Map<String, IClientConfigActions<?>> CONFIG_ACTIONS_MAP = new HashMap<>();
    private static final Gson GSON_INTERNAL_SERIALIZER = new GsonBuilder().create();

    public AcademyCraftClientConfig(@NotNull File configFile) {
        this.configFile = Objects.requireNonNull(configFile, "Config file cannot be null");
        load();
    }

    public static void registerConfigActions(@NotNull String configKey, @NotNull IClientConfigActions<?> actions) {
        Objects.requireNonNull(configKey, "Config key for registration cannot be null");
        Objects.requireNonNull(actions, "ConfigActions for " + configKey + " cannot be null");
        CONFIG_ACTIONS_MAP.put(configKey, actions);
    }

    private void load() {
        if (!configFile.exists() || configFile.length() == 0) {
            this.rootJsonConfig = new JsonObject();
            applyAndSaveDefaults();
            return;
        }

        try (FileReader reader = new FileReader(configFile)) {
            JsonElement parsedElement = JsonParser.parseReader(reader);
            if (parsedElement.isJsonObject()) {
                this.rootJsonConfig = parsedElement.getAsJsonObject();
                if (applyMissingDefaults()) {
                    saveInternal();
                }
            } else {
                this.rootJsonConfig = new JsonObject();
                applyAndSaveDefaults();
            }
        } catch (IOException | JsonSyntaxException e) {
            this.rootJsonConfig = new JsonObject();
            applyAndSaveDefaults();
        }
    }

    private boolean applyDefaultsInternal() {
        boolean changed = false;
        for (Map.Entry<String, IClientConfigActions<?>> entry : CONFIG_ACTIONS_MAP.entrySet()) {
            String configKey = entry.getKey();
            IClientConfigActions<?> actions = entry.getValue();
            if (!this.rootJsonConfig.has(configKey)) {
                this.rootJsonConfig.add(configKey, actions.serializeRaw(actions.getDefaultConfig(), GSON_INTERNAL_SERIALIZER));
                changed = true;
            }
        }
        return changed;
    }

    private void applyAndSaveDefaults() {
        if (applyDefaultsInternal()) {
            saveInternal();
        }
    }

    private boolean applyMissingDefaults() {
        return applyDefaultsInternal();
    }

    private void saveInternal() {
        Gson gsonPretty = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(configFile)) {
            gsonPretty.toJson(this.rootJsonConfig, writer);
         String s =   gsonPretty.toJson(rootJsonConfig);
         AcademyCraft.LOGGER.info(s);
        } catch (Throwable e) {
            AcademyCraft.LOGGER.warn("Failed to save config to {}", configFile.getAbsolutePath(), e);
        }
    }

    public void save() {
        for (Map.Entry<String, Object> cacheEntry : runtimeConfigCache.entrySet()) {
            String configKey = cacheEntry.getKey();
            Object configInstance = cacheEntry.getValue();
            IClientConfigActions<?> actions = CONFIG_ACTIONS_MAP.get(configKey);

            if (actions != null) {
                if (actions.getConfigClass().isInstance(configInstance)) {
                    this.rootJsonConfig.add(configKey, actions.serializeRaw(configInstance, GSON_INTERNAL_SERIALIZER));
                }
            }
        }
        saveInternal();
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T getConfig(@NotNull String configKey, @NotNull Class<T> expectedConfigClass) {
        Objects.requireNonNull(configKey, "Config key cannot be null.");
        Objects.requireNonNull(expectedConfigClass, "Expected config class cannot be null for key: " + configKey);

        if (runtimeConfigCache.containsKey(configKey)) {
            Object cachedConfig = runtimeConfigCache.get(configKey);
            if (expectedConfigClass.isInstance(cachedConfig)) {
                return (T) cachedConfig;
            }
            runtimeConfigCache.remove(configKey);
        }

        IClientConfigActions<?> actionsRaw = CONFIG_ACTIONS_MAP.get(configKey);
        if (actionsRaw == null) {
            AcademyCraft.LOGGER.warn("No config actions registered for key: {}. Returning null.", configKey);
            return null;
        }
        if (!expectedConfigClass.isAssignableFrom(actionsRaw.getConfigClass())) {
            AcademyCraft.LOGGER.warn("Requested config class {} for key {} does not match registered config class {}. Returning null.",
                    expectedConfigClass.getName(), configKey, actionsRaw.getConfigClass().getName());
            return null;
        }
        IClientConfigActions<T> actions = (IClientConfigActions<T>) actionsRaw;

        JsonElement element = this.rootJsonConfig.get(configKey);

        if (element != null && !element.isJsonNull()) {
            return deserializeAndCache(configKey, element, actions);
        } else {
            return deserializeAndCacheDefault(configKey, actions);
        }
    }

    private <T> T deserializeAndCache(String cacheKey, JsonElement element, IClientConfigActions<T> actions) {
        T configInstance;
        try {
            configInstance = actions.deserialize(element, GSON_INTERNAL_SERIALIZER);
        } catch (Exception e) {
            AcademyCraft.LOGGER.warn("Failed to deserialize config for key: {}. Using default. Error: {}", cacheKey, e.getMessage());
            configInstance = actions.getDefaultConfig();
            updateJsonWithDefault(cacheKey, actions, configInstance);
        }
        runtimeConfigCache.put(cacheKey, configInstance);
        return configInstance;
    }

    private <T> T deserializeAndCacheDefault(String cacheKey, IClientConfigActions<T> actions) {
        T configInstance = actions.getDefaultConfig();
        updateJsonWithDefault(cacheKey, actions, configInstance);
        runtimeConfigCache.put(cacheKey, configInstance);
        return configInstance;
    }

    private <T> void updateJsonWithDefault(String key, IClientConfigActions<T> actions, T defaultConfigInstance) {
        JsonElement defaultJson = actions.serialize(defaultConfigInstance, GSON_INTERNAL_SERIALIZER);
        this.rootJsonConfig.add(key, defaultJson);
    }

    public void setConfig(@NotNull String configKey, @NotNull Object configInstance) {
        Objects.requireNonNull(configKey, "Config key cannot be null when setting config.");
        Objects.requireNonNull(configInstance, "Config instance for " + configKey + " cannot be null.");

        IClientConfigActions<?> actions = CONFIG_ACTIONS_MAP.get(configKey);
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
        JsonElement newJsonElement = actions.serializeRaw(configInstance, GSON_INTERNAL_SERIALIZER);
        this.rootJsonConfig.add(configKey, newJsonElement);
        save();
    }
}