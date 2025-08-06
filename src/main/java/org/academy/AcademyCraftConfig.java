package org.academy;

import com.google.gson.*;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import org.academy.api.common.gson.TypeHandler;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AcademyCraftConfig {
    private static final Map<String, TypeHandler<?>> HANDLER_MAP = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().create();

    private final File configFile;
    private JsonObject rootJsonConfig;
    private volatile boolean dirty = false;
    private final Map<String, Object> runtimeConfigCache = new ConcurrentHashMap<>();

    public AcademyCraftConfig(@NotNull File configFile) {
        this.configFile = configFile;
        this.load();
    }

    public static void registerTypeHandler(@NotNull String configKey, @NotNull TypeHandler<?> handler) {
        HANDLER_MAP.put(configKey, handler);
    }

    public static void registerTypeHandler(@NotNull ResourceLocation configKey, @NotNull TypeHandler<?> handler) {
        registerTypeHandler(Util.makeDescriptionId("config", configKey), handler);
    }

    private synchronized void load() {
        if (configFile.exists() && configFile.length() > 0) {
            try (var reader = new FileReader(configFile)) {
                var parsedElement = JsonParser.parseReader(reader);
                rootJsonConfig = parsedElement.isJsonObject() ? parsedElement.getAsJsonObject() : new JsonObject();
            } catch (IOException | JsonSyntaxException e) {
                rootJsonConfig = new JsonObject();
            }
        } else {
            rootJsonConfig = new JsonObject();
        }
        dirty = false;
    }

    public synchronized void save() {
        if (!dirty) {
            return;
        }

        var newRootJson = new JsonObject();
        for (var cacheEntry : runtimeConfigCache.entrySet()) {
            var configKey = cacheEntry.getKey();
            var configInstance = cacheEntry.getValue();
            var handler = HANDLER_MAP.get(configKey);
            if (handler != null) {
                newRootJson.add(configKey, serializeWithHandler(handler, configInstance));
            }
        }
        rootJsonConfig = newRootJson;

        try (var writer = new FileWriter(configFile)) {
            var gsonPretty = new GsonBuilder().setPrettyPrinting().create();
            gsonPretty.toJson(rootJsonConfig, writer);
            dirty = false;
        } catch (Throwable e) {
            AcademyCraft.LOGGER.warn("Failed to save config to {}", configFile.getAbsolutePath(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> JsonElement serializeWithHandler(TypeHandler<T> handler, Object instance) {
        return handler.getAdapter(GSON).toJsonTree((T) instance);
    }

    public <T> T getConfig(@NotNull ResourceLocation configKey) {
        return getConfig(Util.makeDescriptionId("config", configKey));
    }

    @SuppressWarnings("unchecked")
    public <T> T getConfig(@NotNull String configKey) {
        if (runtimeConfigCache.containsKey(configKey)) {
            return (T) runtimeConfigCache.get(configKey);
        }

        var handler = (TypeHandler<T>) HANDLER_MAP.get(configKey);
        if (handler == null) {
            throw new RuntimeException("No TypeHandler registered for key: " + configKey);
        }

        var jsonElement = rootJsonConfig.get(configKey);
        T configInstance;

        if (jsonElement == null || jsonElement.isJsonNull()) {
            configInstance = handler.getDefault();
            this.dirty = true;
        } else {
            configInstance = handler.getAdapter(GSON).fromJsonTree(jsonElement);
        }
        runtimeConfigCache.put(configKey, configInstance);
        return configInstance;
    }

    public void setConfig(@NotNull ResourceLocation configKey, @NotNull Object configInstance) {
        setConfig(Util.makeDescriptionId("config", configKey), configInstance);
    }

    public void setConfig(@NotNull String configKey, @NotNull Object configInstance) {
        var handler = HANDLER_MAP.get(configKey);
        if (handler == null) {
            AcademyCraft.LOGGER.warn("Attempted to set config for unregistered key: {}", configKey);
            return;
        }
        if (!handler.getTypeClass().isInstance(configInstance)) {
            AcademyCraft.LOGGER.warn("Attempted to set config with incorrect type for key: {}. Expected {}, got {}",
                    configKey, handler.getTypeClass().getName(), configInstance.getClass().getName());
            return;
        }

        runtimeConfigCache.put(configKey, configInstance);
        this.dirty = true;
    }
}