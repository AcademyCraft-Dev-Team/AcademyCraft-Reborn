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

/**
 * 音乐室持久化：config/academy/music/rooms.json，保存房间编号/名称/房主/成员与未播完的队列，
 * 服务端关服时写入、启动时读回，避免重启后房间消失喵。
 */
public final class MusicRoomStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = Path.of("config", "academy", "music", "rooms.json");

    private MusicRoomStore() {
    }

    public record Member(String id, String name) {
    }

    public record QueueEntry(SharedTrackEntry entry, String requesterName) {
    }

    /**
     * 单个房间的快照。当前曲目只存播放位置，恢复时以暂停态落盘，避免重启后自动续播喵。
     */
    public record Snapshot(
            String code,
            String name,
            String hostId,
            String hostName,
            List<Member> members,
            List<QueueEntry> queue,
            @Nullable SharedTrackEntry currentEntry,
            String currentRequester,
            float currentPositionSeconds
    ) {
    }

    public static List<Snapshot> load() {
        return load(FILE);
    }

    static List<Snapshot> load(Path file) {
        if (!Files.exists(file)) return List.of();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            var root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) return List.of();
            var roomsElement = root.getAsJsonObject().get("rooms");
            if (roomsElement == null || roomsElement.isJsonNull()) return List.of();
            var type = new TypeToken<List<Snapshot>>() {
            }.getType();
            List<Snapshot> loaded = GSON.fromJson(roomsElement, type);
            if (loaded == null) return List.of();
            List<Snapshot> valid = new ArrayList<>();
            for (var snapshot : loaded) {
                if (snapshot != null && snapshot.code() != null && !snapshot.code().isBlank()) valid.add(snapshot);
            }
            return valid;
        } catch (Exception exception) {
            AcademyCraft.LOGGER.warn("Failed to load music rooms from {}", file, exception);
            return List.of();
        }
    }

    public static void save(List<Snapshot> rooms) {
        save(FILE, rooms);
    }

    static void save(Path file, List<Snapshot> rooms) {
        try {
            var parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
            JsonObject root = new JsonObject();
            root.add("rooms", GSON.toJsonTree(rooms));
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception exception) {
            AcademyCraft.LOGGER.warn("Failed to save music rooms to {}", file, exception);
        }
    }
}
