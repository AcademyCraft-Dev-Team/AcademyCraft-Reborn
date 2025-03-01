package org.academy.internal;

import com.google.gson.*;
import com.google.gson.annotations.SerializedName;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import org.academy.AcademyCraft;
import org.academy.api.common.util.GsonUtil;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AcademyCraftConfig {
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

    @SerializedName("key")
    private final Map<Object, List<Integer>> key = new HashMap<>();

    public List<Integer> getKey(String name, List<Integer> defaultValue) {
        if (!key.containsKey(name)) {
            setKey(name, defaultValue);
        }
        return key.get(name);
    }

    public void setKey(String name, List<Integer> value) {
        key.put(name, value);
        saveConfig();
    }

    public static class Ability {
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

        public void setCpRecoverSpeed(int cpRecoverSpeed) {
            this.cpRecoverSpeed = cpRecoverSpeed;
            saveConfig();
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

        private Skill() {
        }
    }

    public static AcademyCraftConfig loadConfig(File file, Env env) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        if (!isValidConfig(file, env)) {
            return writeDefaultConfig(gson, file, env);
        }
        try (FileReader reader = new FileReader(file)) {
            return gson.fromJson(reader, AcademyCraftConfig.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read config file", e);
        }
    }

    private static boolean isValidConfig(File file, Env env) {
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
            AcademyCraftConfig defaultConfig = getDefaultConfig(env);

            return GsonUtil.isValidField(jsonObject, fields);
        } catch (IOException e) {
            return false;
        }
    }

    private static AcademyCraftConfig writeDefaultConfig(Gson gson, File file, Env env) {
        AcademyCraftConfig defaultConfig = getDefaultConfig(env);
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(defaultConfig, writer);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write default config", e);
        }
        return defaultConfig;
    }

    private static AcademyCraftConfig getDefaultConfig(Env env) {
        AcademyCraftConfig defaultConfig = new AcademyCraftConfig();
        Generic generic = defaultConfig.getGeneric();
        switch (env) {
            case SERVER:
                Ability ability = defaultConfig.getAbility();
                Map<String, List<String>> metalBlocks = ability.getMetalBlocks();
                Map<String, List<String>> metalEntities = ability.getMetalEntities();
                ability.cpRecoverSpeed = 0.0003f;
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

                ability.getSkills().put("railgun", railGun);
                generic.getBooleanMap().put("attackPlayer", true);
                generic.getBooleanMap().put("destroyBlocks", true);
                generic.getBooleanMap().put("genOres", true);
                generic.getBooleanMap().put("genPhaseLiquid", true);
            case CLIENT:
                generic.getBooleanMap().put("useMouseWheel", true);
        }
        return defaultConfig;
    }

    private static void saveConfig() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        File configFile = (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) ? AcademyCraft.clientConfigFile : AcademyCraft.serverConfigFile;
        try (FileWriter writer = new FileWriter(configFile)) {
            gson.toJson(FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT ? AcademyCraft.clientConfig : AcademyCraft.serverConfig, writer);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save config file: " + configFile.getAbsolutePath(), e);
        }
    }

    public enum Env {
        SERVER, CLIENT
    }
}