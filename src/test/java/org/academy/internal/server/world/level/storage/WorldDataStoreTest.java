package org.academy.internal.server.world.level.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorldDataStoreTest {
    @TempDir
    Path directory;

    @Test
    void savesDirtyAbilityDataAndKeepsTheExistingFileOnWriteFailure() throws IOException {
        var target = directory.resolve("academy.json");
        var worldData = new WorldData();
        var playerId = UUID.randomUUID();
        var player = new Player();
        worldData.getPlayers().put(playerId, player);
        player.markDirty();

        assertTrue(WorldDataStore.saveIfDirty(worldData, target));
        assertFalse(player.isDirty());
        var firstSnapshot = Files.readString(target, StandardCharsets.UTF_8);
        assertTrue(firstSnapshot.contains(playerId.toString()));

        player.dismissProgramStarter();
        assertTrue(player.isDirty());
        var unavailableTarget = directory.resolve("unavailable.json");
        Files.createDirectory(unavailableTarget);
        Files.writeString(unavailableTarget.resolve("keep"), "original");
        assertThrows(IOException.class,
                () -> WorldDataStore.saveIfDirty(worldData, unavailableTarget));
        assertTrue(player.isDirty());
        assertEquals("original", Files.readString(unavailableTarget.resolve("keep")));
        assertEquals(firstSnapshot, Files.readString(target, StandardCharsets.UTF_8));

        assertTrue(WorldDataStore.saveIfDirty(worldData, target));
        assertFalse(player.isDirty());
        assertTrue(WorldData.getWorldData(target.toFile()).getPlayers().get(playerId)
                .isProgramStarterDismissed());
    }

    @Test
    void refusesToReplaceAnInvalidExistingSave() throws IOException {
        var target = directory.resolve("academy.json");
        Files.writeString(target, "{broken json", StandardCharsets.UTF_8);

        assertThrows(IllegalStateException.class, () -> WorldData.getWorldData(target.toFile()));
        assertEquals("{broken json", Files.readString(target, StandardCharsets.UTF_8));
    }

    @Test
    void startsEmptyWhenTheSaveDoesNotExist() {
        var target = directory.resolve("academy.json");
        assertTrue(WorldData.getWorldData(target.toFile()).getPlayers().isEmpty());
        assertFalse(Files.exists(target));
    }
}
