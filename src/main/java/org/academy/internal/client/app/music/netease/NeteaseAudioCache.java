package org.academy.internal.client.app.music.netease;

import net.neoforged.fml.loading.FMLPaths;
import org.academy.AcademyCraft;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class NeteaseAudioCache {
    private static final long MAX_IN_MEMORY_BYTES = 64L * 1024L * 1024L;
    private static final Path CACHE_DIR = FMLPaths.GAMEDIR.get()
            .resolve("academy_music").resolve("netease_cache");
    private static final Object LOCK = new Object();
    private static final LinkedHashMap<String, ByteBuffer> MEMORY_CACHE =
            new LinkedHashMap<>(16, 0.75f, true);
    private static final Map<String, CompletableFuture<ByteBuffer>> IN_FLIGHT = new LinkedHashMap<>();
    private static long memoryBytes;

    private NeteaseAudioCache() {
    }

    public static CompletableFuture<ByteBuffer> ensureCachedAsync(String songId) {
        if (songId == null || songId.isBlank()) {
            return CompletableFuture.failedFuture(new IOException("Missing NetEase song id"));
        }
        synchronized (LOCK) {
            var cached = MEMORY_CACHE.get(songId);
            if (cached != null) return CompletableFuture.completedFuture(cached.duplicate());
            var existing = IN_FLIGHT.get(songId);
            if (existing != null) return existing;
            var future = CompletableFuture.supplyAsync(() -> {
                try {
                    var bytes = loadFromDisk(songId);
                    if (bytes == null) {
                        bytes = NeteaseMusicService.downloadStreamBytes(songId);
                        validate(bytes);
                        saveToDisk(songId, bytes);
                    }
                    var buffer = toDirectBuffer(bytes);
                    putInMemory(songId, buffer);
                    return buffer.duplicate();
                } catch (Exception exception) {
                    deleteCorruptFile(resolveFile(songId));
                    throw new RuntimeException(exception);
                }
            }, AcademyCraft.executorService).whenComplete((_, _) -> {
                synchronized (LOCK) {
                    IN_FLIGHT.remove(songId);
                }
            });
            IN_FLIGHT.put(songId, future);
            return future;
        }
    }

    public static boolean isCachedOnDisk(String songId) {
        return songId != null && !songId.isBlank() && Files.exists(resolveFile(songId));
    }

    private static byte[] loadFromDisk(String songId) {
        try {
            var file = resolveFile(songId);
            if (!Files.exists(file)) return null;
            var bytes = Files.readAllBytes(file);
            validate(bytes);
            return bytes;
        } catch (Exception exception) {
            AcademyCraft.LOGGER.warn("Corrupt NetEase audio cache for {}, will re-download", songId);
            return null;
        }
    }

    private static void validate(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < 4) throw new IOException("Empty NetEase audio response");
        var id3 = bytes[0] == 'I' && bytes[1] == 'D' && bytes[2] == '3';
        var mp3Frame = (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xe0) == 0xe0;
        var flac = bytes[0] == 'f' && bytes[1] == 'L' && bytes[2] == 'a' && bytes[3] == 'C';
        if (!id3 && !mp3Frame && !flac) throw new IOException("Unsupported NetEase audio payload");
    }

    private static void saveToDisk(String songId, byte[] bytes) {
        try {
            var file = resolveFile(songId);
            var temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.createDirectories(file.getParent());
            Files.write(temporary, bytes);
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception exception) {
            AcademyCraft.LOGGER.warn("Failed to save NetEase audio cache for {}", songId, exception);
        }
    }

    private static void deleteCorruptFile(Path file) {
        try {
            Files.deleteIfExists(file);
            Files.deleteIfExists(file.resolveSibling(file.getFileName() + ".tmp"));
        } catch (Exception ignored) {
        }
    }

    private static Path resolveFile(String songId) {
        return CACHE_DIR.resolve(songId.replaceAll("[^a-zA-Z0-9_-]", "_") + ".mp3");
    }

    private static ByteBuffer toDirectBuffer(byte[] bytes) {
        Objects.requireNonNull(bytes);
        var buffer = ByteBuffer.allocateDirect(bytes.length);
        buffer.put(bytes).flip();
        return buffer;
    }

    private static void putInMemory(String songId, ByteBuffer buffer) {
        synchronized (LOCK) {
            var previous = MEMORY_CACHE.remove(songId);
            if (previous != null) memoryBytes -= previous.capacity();
            MEMORY_CACHE.put(songId, buffer);
            memoryBytes += buffer.capacity();
            while (memoryBytes > MAX_IN_MEMORY_BYTES && !MEMORY_CACHE.isEmpty()) {
                var eldest = MEMORY_CACHE.entrySet().iterator().next();
                MEMORY_CACHE.remove(eldest.getKey());
                memoryBytes -= eldest.getValue().capacity();
            }
        }
    }
}
