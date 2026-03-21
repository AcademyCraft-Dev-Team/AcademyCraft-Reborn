package org.academy.internal.server.config;

import com.google.gson.annotations.SerializedName;
import org.academy.api.common.gson.TypeHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AbilityConfig {
    public static final String KEY = "ability";

    @SerializedName("metalEntities")
    public final Map<String, List<String>> metalEntities = new HashMap<>();
    @SerializedName("metalBlocks")
    public final Map<String, List<String>> metalBlocks = new HashMap<>();
    @SerializedName("skills")
    public final Map<String, SkillSettings> skills = new HashMap<>();

    @SerializedName("damageMultiplier")
    public float damageMultiplier = 1.0f;
    @SerializedName("cpRatingOffset")
    public float cpRatingOffset = 0.0f;

    public static class SkillSettings {
        @SerializedName("booleanMap")
        public final Map<String, Boolean> booleanMap = new HashMap<>();

        @SerializedName("floatMap")
        public final Map<String, Float> floatMap = new HashMap<>();
    }

    public static class Action implements TypeHandler<AbilityConfig> {
        public static final TypeHandler<AbilityConfig> INSTANCE = new Action();

        private Action() {
        }

        @Override
        public AbilityConfig getDefault() {
            var defaultConfig = new AbilityConfig();

            List<String> minecraftMetalBlocks = new ArrayList<>();
            minecraftMetalBlocks.add("iron_block");
            minecraftMetalBlocks.add("iron_bars");
            minecraftMetalBlocks.add("iron_trapdoor");
            minecraftMetalBlocks.add("gold_block");
            List<String> academyMetalBlocks = new ArrayList<>();
            academyMetalBlocks.add("machine_frame");
            defaultConfig.metalBlocks.put("minecraft", minecraftMetalBlocks);
            defaultConfig.metalBlocks.put("academy", academyMetalBlocks);

            List<String> minecraftMetalEntities = new ArrayList<>();
            minecraftMetalEntities.add("iron_golem");
            List<String> academyMetalEntities = new ArrayList<>();
            academyMetalEntities.add("mag_hook");
            defaultConfig.metalEntities.put("minecraft", minecraftMetalEntities);
            defaultConfig.metalEntities.put("academy", academyMetalEntities);

            var railgunSettings = new SkillSettings();
            railgunSettings.booleanMap.put("enabled", true);
            railgunSettings.booleanMap.put("destroyBlock", true);
            railgunSettings.floatMap.put("damageScale", 1.0f);
            railgunSettings.floatMap.put("cpConsumeSpeed", 1.0f);
            railgunSettings.floatMap.put("overloadConsumeSpeed", 1.0f);
            railgunSettings.floatMap.put("exp_incr_speed", 1.0f);
            defaultConfig.skills.put("railgun", railgunSettings);

            return defaultConfig;
        }

        @Override
        public Class<AbilityConfig> getTypeClass() {
            return AbilityConfig.class;
        }
    }
}