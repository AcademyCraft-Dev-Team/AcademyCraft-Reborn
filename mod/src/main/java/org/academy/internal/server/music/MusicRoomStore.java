package org.academy.internal.server.music;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import org.academy.AcademyCraft;
import org.academy.internal.common.music.SharedTrackEntry;
import org.jspecify.annotations.Nullable;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class MusicRoomStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = Path.of("config", "academy", "music", "rooms.json");

    private MusicRoomStore() {
    }

    public record Member(String id, @Nullable String name) {
    }

    public record QueueEntry(SharedTrackEntry entry, String requesterName) {
    }

    public record Snapshot(
            @Nullable String code,
            String name,
            String hostId,
            String hostName,
            @Nullable List<Member> members,
            @Nullable List<QueueEntry> queue,
            @Nullable SharedTrackEntry currentEntry,
            String currentRequester,
            float currentPositionSeconds
    ) {
    }

    public static List<Snapshot> load() {
        if (!Files.exists(FILE)) return List.of();
        try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            var root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) return List.of();
            var roomsElement = root.getAsJsonObject().get("rooms");
            if (roomsElement == null || roomsElement.isJsonNull()) return List.of();
            var type = new TypeToken<List<Snapshot>>() {
            }.getType();
            List<@Nullable Snapshot> loaded = GSON.fromJson(roomsElement, type);
            if (loaded == null) return List.of();
            List<Snapshot> valid = new ArrayList<>();
            for (var snapshot : loaded) {
                if (snapshot != null && snapshot.code() != null && !snapshot.code().isBlank()) valid.add(snapshot);
            }
            return valid;
        } catch (Exception exception) {
            AcademyCraft.LOGGER.warn("Failed to load music rooms from {}", FILE, exception);
            return List.of();
        }
    }

    public static void save(List<Snapshot> rooms) {
        try {
            var parent = FILE.getParent();
            if (parent != null) Files.createDirectories(parent);
            var root = new JsonObject();
            root.add("rooms", GSON.toJsonTree(rooms));
            try (Writer writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception exception) {
            AcademyCraft.LOGGER.warn("Failed to save music rooms to {}", FILE, exception);
        }
    }
}
