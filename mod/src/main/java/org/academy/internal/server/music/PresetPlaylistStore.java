package org.academy.internal.server.music;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import org.academy.AcademyCraft;
import org.academy.internal.common.music.SharedTrackEntry;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 服务器预设歌单：config/academy/music/server_playlist.json，
 * { "enabled": bool, "mode": "SEQUENTIAL|SHUFFLE|LOOP", "tracks": [SharedTrackEntry...] }。
 * 全服点播队列空闲时按模式自动续播；阶段3提供一键导入命令，本阶段支持手工编辑喵。
 */
public final class PresetPlaylistStore {
    public enum Mode {
        SEQUENTIAL,
        SHUFFLE,
        LOOP
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = Path.of("config", "academy", "music", "server_playlist.json");

    private volatile boolean enabled;
    private volatile Mode mode = Mode.SEQUENTIAL;
    private volatile List<SharedTrackEntry> tracks = List.of();
    private volatile int cursor;

    private PresetPlaylistStore() {
    }

    private static PresetPlaylistStore instance;

    public static PresetPlaylistStore getInstance() {
        if (instance == null) {
            instance = new PresetPlaylistStore();
            instance.load();
        }
        return instance;
    }

    public static void resetForTest() {
        instance = null;
    }

    public boolean isEnabled() {
        return enabled && !tracks.isEmpty();
    }

    public Mode mode() {
        return mode;
    }

    public List<SharedTrackEntry> tracks() {
        return tracks;
    }

    /**
     * 取下一首预设曲目；歌单耗尽（SEQUENTIAL）返回 null。
     */
    public @Nullable SharedTrackEntry next() {
        if (tracks.isEmpty()) return null;
        return switch (mode) {
            case SHUFFLE -> tracks.get(ThreadLocalRandom.current().nextInt(tracks.size()));
            case SEQUENTIAL, LOOP -> {
                var entry = tracks.get(cursor % tracks.size());
                cursor = (cursor + 1) % tracks.size();
                yield entry;
            }
        };
    }

    public void load() {
        enabled = false;
        mode = Mode.SEQUENTIAL;
        tracks = List.of();
        cursor = 0;
        if (!Files.exists(FILE)) return;
        try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            var root = JsonParser.parseReader(reader).getAsJsonObject();
            enabled = root.has("enabled") && root.get("enabled").getAsBoolean();
            mode = parseMode(root.has("mode") ? root.get("mode").getAsString() : "SEQUENTIAL");
            if (root.has("tracks")) {
                var type = new TypeToken<List<SharedTrackEntry>>() {
                }.getType();
                var loaded = GSON.<List<SharedTrackEntry>>fromJson(root.get("tracks"), type);
                if (loaded != null) {
                    List<SharedTrackEntry> valid = new ArrayList<>();
                    for (var entry : loaded) {
                        if (entry != null && !entry.trackId().isBlank()) valid.add(entry);
                    }
                    tracks = Collections.unmodifiableList(valid);
                }
            }
            AcademyCraft.LOGGER.info("Loaded preset playlist: {} tracks, mode={}, enabled={}",
                    tracks.size(), mode, enabled);
        } catch (Exception exception) {
            AcademyCraft.LOGGER.warn("Failed to load preset playlist {}", FILE, exception);
        }
    }

    public void save(boolean newEnabled, Mode newMode, List<SharedTrackEntry> newTracks) {
        enabled = newEnabled;
        mode = newMode;
        tracks = List.copyOf(newTracks);
        cursor = 0;
        try {
            Files.createDirectories(FILE.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("enabled", enabled);
            root.addProperty("mode", mode.name());
            root.add("tracks", GSON.toJsonTree(tracks));
            try (Writer writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException exception) {
            AcademyCraft.LOGGER.warn("Failed to save preset playlist {}", FILE, exception);
        }
    }

    private static Mode parseMode(String raw) {
        try {
            return Mode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return Mode.SEQUENTIAL;
        }
    }
}
