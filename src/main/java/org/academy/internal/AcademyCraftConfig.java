package org.academy.internal;

import com.google.gson.*;
import com.google.gson.annotations.SerializedName;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import org.academy.AcademyCraft;

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

    @SerializedName("gui")
    private final Map<String, Node> gui = new HashMap<>();

    public Map<String, Node> getGui() {
        return gui;
    }

    @SerializedName("key")
    private final Map<Object, List<Integer>> key = new HashMap<>();

    public List<Integer> getKey(String name, List<Integer> defaultValue) {
        return key.getOrDefault(name, defaultValue);
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

    public static class Node {
        @SerializedName("pos")
        double[] pos;

        private Node(double[] pos) {
            this.pos = pos;
        }

        public double[] getPos() {
            return pos;
        }

        public void setPos(double[] pos) {
            this.pos = pos;
            saveConfig();
        }

        private Node() {
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

            for (Field field : fields) {
                String fieldName = field.getName();

                if (!jsonObject.has(fieldName)) {
                    return false;
                }

                JsonElement element = jsonObject.get(fieldName);
                if (!element.isJsonObject()) {
                    return false;
                }

                JsonObject nestedObject = element.getAsJsonObject();

                // ability
                for (Field nestedField : field.getType().getDeclaredFields()) {
                    String nestedFieldName = nestedField.getName();

                    if (!nestedObject.has(nestedFieldName)) {
                        return false;
                    }
                }

                // gui
                if (field.getType().equals(Map.class)) {
                    Map<String, Node> gui = defaultConfig.getGui();
                    JsonObject guiObject = element.getAsJsonObject();

                    for (String string : gui.keySet()) {
                        if (!guiObject.has(string)) {
                            return false;
                        }

                        JsonObject nodeObject = nestedObject.get(string).getAsJsonObject();
                        for (Field nodeField : Node.class.getDeclaredFields()) {
                            String nodeFieldName = nodeField.getName();
                            if (!nodeObject.has(nodeFieldName)) {
                                return false;
                            }
                        }
                    }
                }
            }

            return true;
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
                Map<String, Node> gui = defaultConfig.getGui();
                Map<String, Integer> key = new HashMap<>();
                generic.getBooleanMap().put("useMouseWheel", true);
                gui.put("cpbar", new Node(new double[]{-12, 12}));
                gui.put("keyhint", new Node(new double[]{0, 30}));
                gui.put("media", new Node(new double[]{-6, -6}));
                gui.put("notification", new Node(new double[]{0, 15}));
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