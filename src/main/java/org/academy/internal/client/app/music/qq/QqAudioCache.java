package org.academy.internal.client.app.music.qq;

import net.neoforged.fml.loading.FMLPaths;
import org.academy.AcademyCraft;
import org.academy.internal.client.app.music.decoder.AudioFormatDetector;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class QqAudioCache {
    private static final long MAX_IN_MEMORY_BYTES = 64L * 1024L * 1024L;
    private static final Path CACHE_DIR = FMLPaths.GAMEDIR.get()
            .resolve("academy_music").resolve("qq_cache");
    private static final Object LOCK = new Object();
    private static final LinkedHashMap<String, ByteBuffer> MEMORY_CACHE =
            new LinkedHashMap<>(16, 0.75f, true);
    private static final Map<String, CompletableFuture<ByteBuffer>> IN_FLIGHT =
            new LinkedHashMap<>();
    private static long memoryBytes;

    private QqAudioCache() {
    }

    public static boolean isCachedOnDisk(String mid) {
        return mid != null && !mid.isBlank()
                && (Files.exists(resolveFile(mid)) || Files.exists(resolveLegacyFile(mid)));
    }

    public static CompletableFuture<ByteBuffer> ensureCachedAsync(String mid) {
        if (mid == null || mid.isBlank()) {
            return CompletableFuture.failedFuture(new IOException("Missing QQ music mid"));
        }
        synchronized (LOCK) {
            var cached = MEMORY_CACHE.get(mid);
            if (cached != null) return CompletableFuture.completedFuture(cached.duplicate());
            var existing = IN_FLIGHT.get(mid);
            if (existing != null) return existing;
            var future = CompletableFuture.supplyAsync(() -> {
                try {
                    var bytes = loadFromDisk(mid);
                    if (bytes == null) {
                        bytes = QqMusicService.downloadAudioBytes(mid);
                        validate(bytes);
                        saveToDisk(mid, bytes);
                    }
                    var buffer = toDirectBuffer(bytes);
                    putInMemory(mid, buffer);
                    return buffer.duplicate();
                } catch (Exception exception) {
                    deleteCorruptFiles(mid);
                    throw new RuntimeException(exception);
                }
            }, AcademyCraft.executorService).whenComplete((_, _) -> {
                synchronized (LOCK) {
                    IN_FLIGHT.remove(mid);
                }
            });
            IN_FLIGHT.put(mid, future);
            return future;
        }
    }

    private static byte[] loadFromDisk(String mid) {
        for (var file : new Path[]{resolveFile(mid), resolveLegacyFile(mid)}) {
            try {
                if (!Files.exists(file)) continue;
                var bytes = Files.readAllBytes(file);
                validate(bytes);
                return bytes;
            } catch (Exception exception) {
                AcademyCraft.LOGGER.warn("Corrupt QQ audio cache for {}, will re-download", mid);
                deleteFile(file);
            }
        }
        return null;
    }

    private static void validate(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < 4) throw new IOException("Empty QQ audio response");
        var format = AudioFormatDetector.detect(bytes);
        if (!format.isSupported()) {
            throw new IOException("Unsupported QQ audio payload: " + format);
        }
    }

    private static void saveToDisk(String mid, byte[] bytes) {
        try {
            var file = resolveFile(mid);
            var temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.createDirectories(file.getParent());
            Files.write(temporary, bytes);
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception exception) {
            AcademyCraft.LOGGER.error("Failed to save QQ audio cache for {}", mid, exception);
        }
    }

    private static void deleteCorruptFiles(String mid) {
        deleteFile(resolveFile(mid));
        deleteFile(resolveLegacyFile(mid));
    }

    private static void deleteFile(Path file) {
        try {
            Files.deleteIfExists(file);
            Files.deleteIfExists(file.resolveSibling(file.getFileName() + ".tmp"));
        } catch (Exception ignored) {
        }
    }

    private static Path resolveFile(String mid) {
        return CACHE_DIR.resolve(sanitize(mid) + ".audio");
    }

    private static Path resolveLegacyFile(String mid) {
        return CACHE_DIR.resolve(sanitize(mid) + ".ogg");
    }

    private static void putInMemory(String mid, ByteBuffer buffer) {
        synchronized (LOCK) {
            var previous = MEMORY_CACHE.remove(mid);
            if (previous != null) memoryBytes -= previous.capacity();
            MEMORY_CACHE.put(mid, buffer);
            memoryBytes += buffer.capacity();
            while (memoryBytes > MAX_IN_MEMORY_BYTES && !MEMORY_CACHE.isEmpty()) {
                var eldest = MEMORY_CACHE.entrySet().iterator().next();
                MEMORY_CACHE.remove(eldest.getKey());
                memoryBytes -= eldest.getValue().capacity();
            }
            memoryBytes = Math.max(0L, memoryBytes);
        }
    }

    private static ByteBuffer toDirectBuffer(byte[] bytes) {
        Objects.requireNonNull(bytes);
        var buffer = ByteBuffer.allocateDirect(bytes.length);
        buffer.put(bytes).flip();
        return buffer;
    }

    private static String sanitize(String raw) {
        return raw.replace(':', '_')
                .replace('/', '_')
                .replace('\\', '_')
                .replace('.', '_')
                .replace(' ', '_');
    }
}
