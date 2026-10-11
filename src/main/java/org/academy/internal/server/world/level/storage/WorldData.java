package org.academy.internal.server.world.level.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import org.academy.api.common.util.GsonUtil;
import org.academy.internal.common.skilldata.SkillData;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class WorldData {
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

        try (var fileReader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            JsonObject jsonObject;

            try {
                jsonObject = gson.<@Nullable JsonObject>fromJson(fileReader, JsonObject.class);
            } catch (JsonSyntaxException e) {
                return false;
            }

            if (jsonObject == null) return false;

            var fields = WorldData.class.getDeclaredFields();

            var playersElement = jsonObject.get("players");
            return GsonUtil.isValidField(jsonObject, fields)
                    && playersElement != null && playersElement.isJsonObject();
        } catch (IOException e) {
            return false;
        }
    }

    public static WorldData getWorldData(File file) {
        if (!file.exists()) return new WorldData();
        var gson = createGson();
        if (!isValidFile(file)) {
            throw new IllegalStateException("Invalid Academy world data file: " + file.getAbsolutePath());
        }

        try (var reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            var worldData = gson.fromJson(reader, WorldData.class);
            if (worldData == null || worldData.players == null
                    || worldData.players.values().stream().anyMatch(Objects::isNull)) {
                throw new IllegalStateException("Invalid Academy world data file: " + file.getAbsolutePath());
            }
            worldData.migrateLegacyData();
            return worldData;
        } catch (IOException | JsonSyntaxException e) {
            throw new RuntimeException("Failed to read world data file", e);
        }
    }

    boolean migrateLegacyData() {
        var changed = false;
        for (var player : players.values()) changed |= player.migrateLegacyData();
        return changed;
    }

    public Map<UUID, Player> getPlayers() {
        return players;
    }
}
