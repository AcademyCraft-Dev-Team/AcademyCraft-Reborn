package org.academy.internal.server.world.level.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import org.academy.AbilitySystemServer;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftServer;
import org.academy.api.common.util.GsonUtil;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;

public class AcademyCraftWorldData {
    @SerializedName("players")
    private final Map<UUID, Player> players = new HashMap<>();

    public Map<UUID, Player> getPlayers() {
        return players;
    }

    public static class Player {
        @SerializedName("abilityCategory")
        private String abilityCategory;

        public final String getAbilityCategory() {
            return abilityCategory;
        }

        public final void setAbilityCategory(String abilityCategory) {
            this.abilityCategory = abilityCategory;
        }

        @SerializedName("skills")
        private final List<String> skills = new ArrayList<>();

        public final List<String> getSkills() {
            return skills;
        }

        @SerializedName("level")
        private volatile int level;

        public final int getLevel() {
            return level;
        }

        public final void setLevel(int level) {
            this.level = level;
        }

        @SerializedName("computingPower")
        private volatile float computingPower = 0f;

        public final float getComputingPower() {
            return computingPower;
        }

        public final void setComputingPower(float computingPower) {
            AbilitySystemServer.addTask(() -> this.computingPower = Math.min(getMaximumComputingPower(), computingPower));
        }

        @SerializedName("maximumComputingPower")
        private volatile float maximumComputingPower = 100f;

        public float getMaximumComputingPower() {
            return maximumComputingPower;
        }

        public void setMaximumComputingPower(float maximumComputingPower) {
            AbilitySystemServer.addTask(() -> this.maximumComputingPower = maximumComputingPower);
        }

        @SerializedName("computingPowerRecoverySpeed")
        private volatile float computingPowerRecoverySpeed = 1f;

        public float getComputingPowerRecoverySpeed() {
            return computingPowerRecoverySpeed;
        }

        public void setComputingPowerRecoverySpeed(float computingPowerRecoverySpeed) {
            AbilitySystemServer.addTask(() -> this.computingPowerRecoverySpeed = computingPowerRecoverySpeed);
        }
    }

    private static boolean isValidFile(File file) {
        final Gson gson = new GsonBuilder().create();

        try (FileReader fileReader = new FileReader(file)) {
            final JsonObject jsonObject;

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
        final Gson gson = new GsonBuilder().setPrettyPrinting().create();
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
        if (AcademyCraftServer.academyCraftWorldData == null) {
            return;
        }
        final Gson gson = new GsonBuilder().setPrettyPrinting().create();
        final File configFile = AcademyCraftServer.worldDataFile;

        try (FileWriter fileWriter = new FileWriter(configFile)) {
            gson.toJson(AcademyCraftServer.academyCraftWorldData, fileWriter);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save world data", e);
        }
    }
}