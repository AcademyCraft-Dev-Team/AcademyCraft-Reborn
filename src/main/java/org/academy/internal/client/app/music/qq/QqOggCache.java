package org.academy.internal.client.app.music.qq;

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

public final class QqOggCache {
    private static final long MAX_IN_MEMORY_BYTES = 64L * 1024L * 1024L;

    private static final Path CACHE_DIR = FMLPaths.GAMEDIR.get()
            .resolve("academy_music").resolve("qq_cache");

    private static final Object LOCK = new Object();
    private static final LinkedHashMap<String, ByteBuffer> MEMORY_CACHE = new LinkedHashMap<>(16, 0.75f, true);
    private static final Map<String, CompletableFuture<ByteBuffer>> IN_FLIGHT = new LinkedHashMap<>();
    private static long memoryBytes = 0L;

    private QqOggCache() {
    }

    public static boolean hasInMemory(String mid) {
        synchronized (LOCK) {
            return MEMORY_CACHE.containsKey(mid);
        }
    }

    public static boolean isCachedOnDisk(String mid) {
        if (mid == null || mid.isBlank()) {
            return false;
        }
        try {
            return Files.exists(resolveFile(mid));
        } catch (Exception e) {
            return false;
        }
    }

    public static ByteBuffer getInMemoryBuffer(String mid) {
        synchronized (LOCK) {
            return MEMORY_CACHE.get(mid);
        }
    }

    public static CompletableFuture<ByteBuffer> ensureCachedAsync(String mid) {
        if (mid == null || mid.isBlank()) {
            return CompletableFuture.failedFuture(new IOException("Missing QQ music mid"));
        }

        synchronized (LOCK) {
            var cached = MEMORY_CACHE.get(mid);
            if (cached != null) {
                return CompletableFuture.completedFuture(cached);
            }
            var existing = IN_FLIGHT.get(mid);
            if (existing != null) {
                return existing;
            }
            var future = CompletableFuture.supplyAsync(() -> {
                try {
                    var buffer = loadFromDisk(mid);
                    if (buffer == null) {
                        var bytes = QqMusicService.downloadOggBytes(mid);
                        buffer = toDirectBuffer(bytes);
                        saveToDisk(mid, bytes);
                    }
                    putInMemory(mid, buffer);
                    return buffer;
                } catch (Exception e) {
                    deleteCorruptFile(resolveFile(mid), mid);
                    throw new RuntimeException(e);
                }
            }, AcademyCraft.executorService).whenComplete((buf, throwable) -> {
                synchronized (LOCK) {
                    IN_FLIGHT.remove(mid);
                }
            });
            IN_FLIGHT.put(mid, future);
            return future;
        }
    }

    private static ByteBuffer loadFromDisk(String mid) {
        try {
            var file = resolveFile(mid);
            if (!Files.exists(file)) {
                return null;
            }
            var bytes = Files.readAllBytes(file);
            if (bytes.length < 4 || bytes[0] != 'O' || bytes[1] != 'g' || bytes[2] != 'g' || bytes[3] != 'S') {
                deleteCorruptFile(file, mid);
                AcademyCraft.LOGGER.warn("Corrupt QQ ogg cache for {}, will re-download", mid);
                return null;
            }
            return toDirectBuffer(bytes);
        } catch (Exception e) {
            deleteCorruptFile(resolveFile(mid), mid);
            AcademyCraft.LOGGER.warn("Corrupt QQ ogg cache for {}, will re-download", mid);
            return null;
        }
    }

    private static void saveToDisk(String mid, byte[] bytes) {
        try {
            var file = resolveFile(mid);
            var tempFile = file.resolveSibling(file.getFileName() + ".tmp");
            Files.createDirectories(file.getParent());
            Files.write(tempFile, bytes);
            Files.move(tempFile, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            AcademyCraft.LOGGER.error("Failed to save QQ ogg cache for {}", mid, e);
        }
    }

    private static void deleteCorruptFile(Path file, String mid) {
        try {
            Files.deleteIfExists(file);
            var tempFile = file.resolveSibling(file.getFileName() + ".tmp");
            Files.deleteIfExists(tempFile);
        } catch (Exception ignored) {
        }
    }

    private static Path resolveFile(String mid) {
        return CACHE_DIR.resolve(sanitize(mid) + ".ogg");
    }

    private static void putInMemory(String mid, ByteBuffer buffer) {
        if (buffer == null) {
            return;
        }
        synchronized (LOCK) {
            var existing = MEMORY_CACHE.remove(mid);
            if (existing != null) {
                memoryBytes -= existing.capacity();
            }
            MEMORY_CACHE.put(mid, buffer);
            memoryBytes += buffer.capacity();
            trimIfNeeded();
        }
    }

    private static void trimIfNeeded() {
        while (memoryBytes > MAX_IN_MEMORY_BYTES && !MEMORY_CACHE.isEmpty()) {
            var eldest = MEMORY_CACHE.entrySet().iterator().next();
            MEMORY_CACHE.remove(eldest.getKey());
            var buffer = eldest.getValue();
            if (buffer != null) {
                memoryBytes -= buffer.capacity();
            }
        }
        memoryBytes = Math.max(0L, memoryBytes);
    }

    private static ByteBuffer toDirectBuffer(byte[] bytes) {
        Objects.requireNonNull(bytes);
        var buffer = ByteBuffer.allocateDirect(bytes.length);
        buffer.put(bytes);
        buffer.flip();
        return buffer;
    }

    private static String sanitize(String raw) {
        if (raw == null) {
            return "null";
        }
        return raw.replace(':', '_')
                .replace('/', '_')
                .replace('\\', '_')
                .replace('.', '_')
                .replace(' ', '_');
    }
}
