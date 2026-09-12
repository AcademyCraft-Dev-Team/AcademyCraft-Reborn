package org.academy.internal.server.music;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicRoomStoreTest {
    private static MusicRoomStore.Snapshot snapshot(String code, String name) {
        return new MusicRoomStore.Snapshot(
                code,
                name,
                "11111111-1111-1111-1111-111111111111",
                "Steve",
                List.of(
                        new MusicRoomStore.Member("11111111-1111-1111-1111-111111111111", "Steve"),
                        new MusicRoomStore.Member("22222222-2222-2222-2222-222222222222", "Alex")
                ),
                List.of(new MusicRoomStore.QueueEntry(
                        new org.academy.internal.common.music.SharedTrackEntry(
                                "netease", "999", "Song", "Artist", 180, false, "https://x/y.jpg"),
                        "Alex")),
                new org.academy.internal.common.music.SharedTrackEntry(
                        "netease", "100", "Now", "Singer", 200, true, ""),
                "Steve",
                12.5f
        );
    }

    @Test
    void saveThenLoadRoundTripsAllFields(@TempDir Path dir) {
        var file = dir.resolve("rooms.json");
        MusicRoomStore.save(file, List.of(snapshot("AB12", "Room")));

        var loaded = MusicRoomStore.load(file);
        assertEquals(1, loaded.size());
        var restored = loaded.get(0);
        assertEquals("AB12", restored.code());
        assertEquals("Room", restored.name());
        assertEquals("Steve", restored.hostName());
        assertEquals(2, restored.members().size());
        assertEquals("Alex", restored.members().get(1).name());
        assertEquals(1, restored.queue().size());
        assertEquals("999", restored.queue().get(0).entry().trackId());
        assertEquals("100", restored.currentEntry().trackId());
        assertTrue(restored.currentEntry().vip());
        assertEquals("Steve", restored.currentRequester());
        assertEquals(12.5f, restored.currentPositionSeconds(), 0.001f);
    }

    @Test
    void missingFileReturnsEmpty(@TempDir Path dir) {
        assertTrue(MusicRoomStore.load(dir.resolve("absent.json")).isEmpty());
    }

    @Test
    void corruptFileReturnsEmptyAndDoesNotThrow(@TempDir Path dir) throws Exception {
        var file = dir.resolve("rooms.json");
        Files.writeString(file, "{ this is not json", StandardCharsets.UTF_8);
        assertTrue(MusicRoomStore.load(file).isEmpty());
    }

    @Test
    void blankCodeEntriesAreFiltered(@TempDir Path dir) {
        var file = dir.resolve("rooms.json");
        MusicRoomStore.save(file, List.of(snapshot("", "NoCode"), snapshot("OK01", "Good")));

        var loaded = MusicRoomStore.load(file);
        assertEquals(1, loaded.size());
        assertEquals("OK01", loaded.get(0).code());
    }

    @Test
    void emptyListRoundTripsToEmpty(@TempDir Path dir) {
        var file = dir.resolve("rooms.json");
        MusicRoomStore.save(file, List.of());
        assertTrue(MusicRoomStore.load(file).isEmpty());
    }
}
