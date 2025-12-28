package org.academy.internal.server.world.level.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftServer;
import org.academy.api.common.util.GsonUtil;
import org.academy.internal.common.skilldata.SkillData;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class WorldData {
    private static final Logger LOGGER = AcademyCraft.getLogger();
    
    @SerializedName("players")
    private final Map<UUID, Player> players = new HashMap<>();

    private static Gson createGson() {
        return new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(SkillData.class, new SkillDataSerializer<>())
                .create();
    }

    private static boolean isValidFile(File file) {
        final var gson = createGson();

        try (var fileReader = new FileReader(file)) {
            final JsonObject jsonObject;

            try {
                jsonObject = gson.fromJson(fileReader, JsonObject.class);
            } catch (JsonSyntaxException e) {
                return false;
            }

            if (jsonObject == null) {
                return false;
            }

            var fields = WorldData.class.getDeclaredFields();

            return GsonUtil.isValidField(jsonObject, fields);
        } catch (IOException e) {
            return false;
        }
    }

    public static WorldData getWorldData(File file) {
        final var gson = createGson();
        if (!isValidFile(file)) {
            var worldData = new WorldData();
            LOGGER.debug("Creating new world data file.");
            try (var fileWriter = new FileWriter(file)) {
                gson.toJson(worldData, fileWriter);
            } catch (IOException e) {
                throw new RuntimeException("Failed to create world data file", e);
            }
            return worldData;
        }

        try (var reader = new FileReader(file)) {
            return gson.fromJson(reader, WorldData.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read world data file", e);
        }
    }

    public static void saveData() {
        if (AcademyCraftServer.worldData == null) return;

        var hasDirtyData = AcademyCraftServer.worldData.getPlayers().values().stream()
                .anyMatch(Player::isDirty);

        if (!hasDirtyData) return;

        LOGGER.debug("Dirty data detected, saving world data...");
        var gson = createGson();
        var worldDataFile = AcademyCraftServer.worldDataFile;

         if (worldDataFile == null) throw new IllegalStateException("World data file has not been set.");

        try (var fileWriter = new FileWriter(worldDataFile)) {
            gson.toJson(AcademyCraftServer.worldData, fileWriter);
        } catch (IOException e) {
            LOGGER.error("Failed to save world data", e);
            return;
        }

        AcademyCraftServer.worldData.getPlayers().values().forEach(Player::clean);
        LOGGER.debug("World data saved and dirty flags cleaned.");
    }

    public Map<UUID, Player> getPlayers() {
        return players;
    }
}