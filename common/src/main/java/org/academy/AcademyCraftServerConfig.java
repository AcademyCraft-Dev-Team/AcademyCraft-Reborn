package org.academy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import org.academy.api.common.util.GsonUtil;
import org.academy.api.server.ability.AbilitySystemServer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AcademyCraftServerConfig {
    @SerializedName("ability")
    private final Ability ability = new Ability();
    @SerializedName("generic")
    private final Generic generic = new Generic();

    public AcademyCraftServerConfig() {}

    private File getConfigurationFile() {
        return AcademyCraftServer.serverConfigFile;
    }

    public Ability getAbility() {
        return ability;
    }

    public Generic getGeneric() {
        return generic;
    }

    public AcademyCraftServerConfig loadConfig() {
        File file = getConfigurationFile();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        if (!isValidConfig(file)) {
            return writeDefaultConfig(gson, file);
        }
        try (FileReader reader = new FileReader(file)) {
            AcademyCraftServerConfig loadedConfig = gson.fromJson(reader, AcademyCraftServerConfig.class);
            if (loadedConfig == null) {
                AcademyCraft.LOGGER.warn("Config file {} was empty or malformed, writing default.", file.getAbsolutePath());
                return writeDefaultConfig(gson, file);
            }
            return loadedConfig;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read config file: " + file.getAbsolutePath(), e);
        }
    }

    private boolean isValidConfig(File file) {
        Gson gson = new GsonBuilder().create();
        try (FileReader fileReader = new FileReader(file)) {
            JsonObject jsonObject;
            try {
                jsonObject = gson.fromJson(fileReader, JsonObject.class);
            } catch (JsonSyntaxException e) {
                return false;
            }
            if (jsonObject == null) {
                return false;
            }
            List<Field> relevantFields = new ArrayList<>();
            for (Field field : AcademyCraftServerConfig.class.getDeclaredFields()) {
                if (field.isAnnotationPresent(SerializedName.class)) {
                    relevantFields.add(field);
                }
            }
            return GsonUtil.isValidField(jsonObject, relevantFields.toArray(new Field[0]));
        } catch (IOException e) {
            return false;
        }
    }

    private AcademyCraftServerConfig writeDefaultConfig(Gson gson, File file) {
        AcademyCraftServerConfig defaultConfig = new AcademyCraftServerConfig();
        writeDefaultConfigValues(defaultConfig);
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(defaultConfig, writer);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write default config: " + file.getAbsolutePath(), e);
        }
        return defaultConfig;
    }

    private void writeDefaultConfigValues(AcademyCraftServerConfig academyCraftConfig) {
        Ability abilityConfig = academyCraftConfig.getAbility();
        Map<String, List<String>> metalBlocks = abilityConfig.getMetalBlocks();
        Map<String, List<String>> metalEntities = abilityConfig.getMetalEntities();
        Skill railGun = new Skill();
        railGun.getBooleanMap().put("enabled", true);
        railGun.getBooleanMap().put("destroyBlock", true);
        railGun.getFloatMap().put("damageScale", 1.0f);
        railGun.getFloatMap().put("cpConsumeSpeed", 1.0f);
        railGun.getFloatMap().put("overloadConsumeSpeed", 1.0f);
        railGun.getFloatMap().put("exp_incr_speed", 1.0f);

        List<String> minecraftMetalBlocks = new ArrayList<>();
        minecraftMetalBlocks.add("iron_block");
        minecraftMetalBlocks.add("iron_bars");
        minecraftMetalBlocks.add("iron_trapdoor");
        minecraftMetalBlocks.add("gold_block");
        List<String> academyMetalBlocks = new ArrayList<>();
        academyMetalBlocks.add("machine_frame");
        metalBlocks.put("minecraft", minecraftMetalBlocks);
        metalBlocks.put("academy", academyMetalBlocks);

        List<String> minecraftMetalEntities = new ArrayList<>();
        minecraftMetalEntities.add("villager_golem");
        List<String> academyMetalEntities = new ArrayList<>();
        academyMetalEntities.add("mag_hook");
        metalEntities.put("minecraft", minecraftMetalEntities);
        metalEntities.put("academy", academyMetalEntities);

        abilityConfig.getSkills().put("railgun", railGun);
        Generic genericConfig = academyCraftConfig.getGeneric();
        genericConfig.getBooleanMap().put("attackPlayer", true);
        genericConfig.getBooleanMap().put("destroyBlocks", true);
        genericConfig.getBooleanMap().put("genOres", true);
        genericConfig.getBooleanMap().put("genPhaseLiquid", true);
    }

    public void save() {
        File configFile = getConfigurationFile();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(configFile)) {
            gson.toJson(this, writer);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to save config file: " + configFile.getAbsolutePath(), e);
        }
    }

    public static class Ability {
        @SerializedName("metalEntities")
        private final Map<String, List<String>> metalEntities = new HashMap<>();
        @SerializedName("metalBlocks")
        private final Map<String, List<String>> metalBlocks = new HashMap<>();
        @SerializedName("skills")
        private final Map<String, Skill> skills = new HashMap<>();
        @SerializedName("damageMultiplier")
        private volatile float damageMultiplier = 1.0f;
        @SerializedName("cpRecoverSpeed")
        private float cpRecoverSpeed;

        private Ability() {
        }

        public float getDamageMultiplier() {
            return damageMultiplier;
        }

        public void setDamageMultiplier(float damageMultiplier) {
            AbilitySystemServer.addTask(() -> this.damageMultiplier = damageMultiplier);
        }

        public float getCpRecoverSpeed() {
            return cpRecoverSpeed;
        }

        public void setCpRecoverSpeed(float cpRecoverSpeed) {
            this.cpRecoverSpeed = cpRecoverSpeed;
            AcademyCraftServer.serverConfig.save();
        }

        public Map<String, List<String>> getMetalEntities() {
            return metalEntities;
        }

        public Map<String, List<String>> getMetalBlocks() {
            return metalBlocks;
        }

        public Map<String, Skill> getSkills() {
            return skills;
        }
    }

    public static class Generic {
        @SerializedName("booleanMap")
        private final Map<String, Boolean> booleanMap = new HashMap<>();

        @SerializedName("stringArrayMap")
        private final Map<String, String[]> stringArrayMap = new HashMap<>();

        private Generic() {
        }

        public Map<String, Boolean> getBooleanMap() {
            return booleanMap;
        }

        public Map<String, String[]> getStringArrayMap() {
            return stringArrayMap;
        }
    }

    public static class Skill {
        @SerializedName("booleanMap")
        private final Map<String, Boolean> booleanMap = new HashMap<>();

        @SerializedName("floatMap")
        private final Map<String, Float> floatMap = new HashMap<>();

        protected Skill() {
        }

        public Map<String, Boolean> getBooleanMap() {
            return booleanMap;
        }

        public Map<String, Float> getFloatMap() {
            return floatMap;
        }
    }
}