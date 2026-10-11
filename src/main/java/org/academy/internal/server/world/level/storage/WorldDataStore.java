package org.academy.internal.server.world.level.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Writes Academy player data without replacing a valid save until the new snapshot is complete. */
public final class WorldDataStore {
    private WorldDataStore() {
    }

    public static boolean saveIfDirty(WorldData worldData, Path target) throws IOException {
        var dirtyPlayers = worldData.getPlayers().values().stream().filter(Player::isDirty).toList();
        if (dirtyPlayers.isEmpty()) return false;

        var snapshot = WorldData.createGson().toJson(worldData);
        writeAtomically(target, snapshot);
        dirtyPlayers.forEach(Player::clean);
        return true;
    }

    private static void writeAtomically(Path target, String snapshot) throws IOException {
        var absoluteTarget = target.toAbsolutePath();
        var temporary = Files.createTempFile(absoluteTarget.getParent(),
                absoluteTarget.getFileName().toString(), ".tmp");
        try {
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                var bytes = ByteBuffer.wrap(snapshot.getBytes(StandardCharsets.UTF_8));
                while (bytes.hasRemaining()) channel.write(bytes);
                channel.force(true);
            }
            try {
                Files.move(temporary, absoluteTarget,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, absoluteTarget, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
