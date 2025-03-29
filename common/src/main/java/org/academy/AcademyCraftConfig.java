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
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unchecked")
public class AcademyCraftConfig<T extends AcademyCraftConfig<T>> {
    @SerializedName("ability")
    private final Ability ability = new Ability();

    public Ability getAbility() {
        return ability;
    }

    @SerializedName("generic")
    private final Generic generic = new Generic();

    public Generic getGeneric() {
        return generic;
    }

    public static class Ability {
        @SerializedName("damageMultiplier")
        private volatile float damageMultiplier = 1.0f;

        public float getDamageMultiplier() {
            return damageMultiplier;
        }

        public void setDamageMultiplier(float damageMultiplier) {
            AbilitySystemServer.addTask(() -> this.damageMultiplier = damageMultiplier);
        }

        @SerializedName("cpRecoverSpeed")
        private float cpRecoverSpeed;

        @SerializedName("metalEntities")
        private final Map<String, List<String>> metalEntities = new HashMap<>();

        @SerializedName("metalBlocks")
        private final Map<String, List<String>> metalBlocks = new HashMap<>();

        @SerializedName("skills")
        private final Map<String, Skill> skills = new HashMap<>();

        public float getCpRecoverSpeed() {
            return cpRecoverSpeed;
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

        public void setCpRecoverSpeed(float cpRecoverSpeed) {
            this.cpRecoverSpeed = cpRecoverSpeed;
            AcademyCraft.saveConfig();
        }

        private Ability() {
        }
    }

    public static class Generic {
        @SerializedName("booleanMap")
        private final Map<String, Boolean> booleanMap = new HashMap<>();

        @SerializedName("stringArrayMap")
        private final Map<String, String[]> stringArrayMap = new HashMap<>();

        public Map<String, Boolean> getBooleanMap() {
            return booleanMap;
        }

        public Map<String, String[]> getStringArrayMap() {
            return stringArrayMap;
        }

        private Generic() {
        }
    }

    public static class Skill {
        @SerializedName("booleanMap")
        private final Map<String, Boolean> booleanMap = new HashMap<>();

        @SerializedName("floatMap")
        private final Map<String, Float> floatMap = new HashMap<>();

        public Map<String, Boolean> getBooleanMap() {
            return booleanMap;
        }

        public Map<String, Float> getFloatMap() {
            return floatMap;
        }

        protected Skill() {
        }
    }

    public T loadConfig(File file) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        if (!isValidConfig(file)) {
            return writeDefaultConfig(gson, file);
        }
        try (FileReader reader = new FileReader(file)) {
            Type type = getClass();
            return gson.fromJson(reader, type);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read config file", e);
        }
    }

    protected boolean isValidConfig(File file) {
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

            Field[] fields = AcademyCraftConfig.class.getDeclaredFields();

            return GsonUtil.isValidField(jsonObject, fields);
        } catch (IOException e) {
            return false;
        }
    }

    private T writeDefaultConfig(Gson gson, File file) {
        T defaultConfig = getDefaultConfig();
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(defaultConfig, writer);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write default config", e);
        }
        return defaultConfig;
    }

    private T getDefaultConfig() {
        T defaultConfig = createDefaultConfigInstance();
        writeDefaultConfig(defaultConfig);
        return defaultConfig;
    }

    private T createDefaultConfigInstance() {
        try {
            return (T) this.getClass().getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create default config instance", e);
        }
    }

    protected void writeDefaultConfig(T academyCraftConfig) {
    }
}