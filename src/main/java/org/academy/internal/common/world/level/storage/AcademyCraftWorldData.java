package org.academy.internal.common.world.level.storage;

import com.google.gson.*;
import com.google.gson.annotations.SerializedName;
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

public class AcademyCraftWorldData {
    @SerializedName("players")
    private final Map<String, Player> players = new HashMap<>();

    public Map<String, Player> getPlayers() {
        return players;
    }

    public static class Player {
        @SerializedName("abilityCategory")
        private String abilityCategory;

        public String getAbilityCategory() {
            return abilityCategory;
        }

        public void setAbilityCategory(String abilityCategory) {
            this.abilityCategory = abilityCategory;
            saveData();
        }

        @SerializedName("skills")
        private List<String> skills = new ArrayList<>();

        public List<String> getSkills() {
            return skills;
        }

        @SerializedName("level")
        private int level;

        public int getLevel() {
            return level;
        }

        public void setLevel(int level) {
            this.level = level;
            saveData();
        }
    }

    private static boolean isValidFile(File file) {
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

            Field[] fields = AcademyCraftWorldData.class.getDeclaredFields();

            return GsonUtil.isValidField(jsonObject, fields);
        } catch (IOException e) {
            return false;
        }
    }

    public static AcademyCraftWorldData getWorldData(File file) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        if (!isValidFile(file)) {
            AcademyCraftWorldData worldData = new AcademyCraftWorldData();
            AcademyCraft.LOGGER.info("Creating new world data file.");
            try (FileWriter fileWriter = new FileWriter(file)) {
                gson.toJson(worldData, fileWriter);
            } catch (IOException e) {
                throw new RuntimeException("Failed to create world data file", e);
            }
            return worldData;
        }

        try (FileReader reader = new FileReader(file)) {
            return gson.fromJson(reader, AcademyCraftWorldData.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read world data file", e);
        }
    }

    public static void saveData() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        File configFile = AcademyCraft.worldDataFile;

        try (FileWriter fileWriter = new FileWriter(configFile)) {
            gson.toJson(AcademyCraft.academyCraftWorldData, fileWriter);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save world data", e);
        }
    }
}