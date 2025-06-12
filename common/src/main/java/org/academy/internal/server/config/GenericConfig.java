package org.academy.internal.server.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import org.academy.api.common.config.IConfigAction;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class GenericConfig {
    public static final String KEY = "generic";

    @SerializedName("booleanMap")
    public final Map<String, Boolean> booleanMap = new HashMap<>();

    public static final class Action implements IConfigAction<GenericConfig> {
        public static final IConfigAction<GenericConfig> INSTANCE = new Action();

        private Action(){
        }

        @Override
        public @NotNull GenericConfig deserialize(@NotNull JsonElement jsonElement, @NotNull Gson gson) {
            return gson.fromJson(jsonElement, GenericConfig.class);
        }

        @Override
        public @NotNull JsonElement serialize(@NotNull GenericConfig configInstance, @NotNull Gson gson) {
            return gson.toJsonTree(configInstance);
        }

        @Override
        public @NotNull GenericConfig getDefaultConfig() {
            GenericConfig defaultConfig = new GenericConfig();
            defaultConfig.booleanMap.put("attackPlayer", true);
            defaultConfig.booleanMap.put("destroyBlocks", true);
            defaultConfig.booleanMap.put("genOres", true);
            defaultConfig.booleanMap.put("genPhaseLiquid", true);
            return defaultConfig;
        }

        @Override
        public @NotNull Class<GenericConfig> getConfigClass() {
            return GenericConfig.class;
        }
    }
}