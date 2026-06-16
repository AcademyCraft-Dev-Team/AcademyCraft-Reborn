package org.academy.internal.server.config;

import com.google.gson.annotations.SerializedName;
import org.academy.api.common.gson.TypeHandler;

import java.util.HashMap;
import java.util.Map;

public class GenericConfig {
    public static final String KEY = "generic";

    @SerializedName("booleanMap")
    public final Map<String, Boolean> booleanMap = new HashMap<>();

    public static final class Action implements TypeHandler<GenericConfig> {
        public static final TypeHandler<GenericConfig> INSTANCE = new Action();

        private Action(){
        }

        @Override
        public GenericConfig getDefault() {
            var defaultConfig = new GenericConfig();
            defaultConfig.booleanMap.put("attackPlayer", true);
            defaultConfig.booleanMap.put("destroyBlocks", true);
            defaultConfig.booleanMap.put("genOres", true);
            defaultConfig.booleanMap.put("genPhaseLiquid", true);
            defaultConfig.booleanMap.put("devMode", false);
            return defaultConfig;
        }

        @Override
        public Class<GenericConfig> getTypeClass() {
            return GenericConfig.class;
        }
    }
}