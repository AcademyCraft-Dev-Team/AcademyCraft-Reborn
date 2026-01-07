package org.academy.internal.server.world.level.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import org.academy.AcademyCraft;
import org.academy.api.common.util.GsonUtil;
import org.academy.internal.common.skilldata.SkillData;
import org.jspecify.annotations.Nullable;
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

    public static Gson createGson() {
        return new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(SkillData.class, new SkillDataSerializer<>())
                .create();
    }

    private static boolean isValidFile(File file) {
        var gson = createGson();

        try (var fileReader = new FileReader(file)) {
            JsonObject jsonObject;

            try {
                jsonObject = gson.<@Nullable JsonObject>fromJson(fileReader, JsonObject.class);
            } catch (JsonSyntaxException e) {
                return false;
            }

            if (jsonObject == null) return false;

            var fields = WorldData.class.getDeclaredFields();

            return GsonUtil.isValidField(jsonObject, fields);
        } catch (IOException e) {
            return false;
        }
    }

    public static WorldData getWorldData(File file) {
        var gson = createGson();
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

    public Map<UUID, Player> getPlayers() {
        return players;
    }
}