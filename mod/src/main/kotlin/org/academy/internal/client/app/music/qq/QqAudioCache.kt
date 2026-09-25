package org.academy.internal.client.app.music.qq

import net.neoforged.fml.loading.FMLPaths
import org.academy.AcademyCraft
import org.academy.internal.client.app.music.decoder.AudioFormatDetector
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.CompletableFuture

object QqAudioCache {
    private const val MAX_IN_MEMORY_BYTES = 64L * 1024L * 1024L
    private val CACHE_DIR: Path by lazy {
        FMLPaths.GAMEDIR.get().resolve("academy_music").resolve("qq_cache")
    }
    private val LOCK = Any()
    private val MEMORY_CACHE = LinkedHashMap<String, ByteBuffer>(16, 0.75f, true)
    private val IN_FLIGHT = LinkedHashMap<String, CompletableFuture<ByteBuffer>>()
    private var memoryBytes = 0L

    /**
     * 收录服务器共享账号解析得到的音频（校验后落盘并入内存缓存）。
     * 同步执行，避免在双线程池上嵌套 join 造成死锁喵。
     */
    fun acceptServerResolved(mid: String, bytes: ByteArray): CompletableFuture<ByteBuffer> {
        return try {
            validate(bytes)
            saveToDisk(mid, bytes)
            val buffer = toDirectBuffer(bytes)
            putInMemory(mid, buffer)
            CompletableFuture.completedFuture(buffer.duplicate())
        } catch (exception: Exception) {
            CompletableFuture.failedFuture(exception)
        }
    }

    fun ensureCachedAsync(mid: String?): CompletableFuture<ByteBuffer> {
        if (mid.isNullOrBlank()) {
            return CompletableFuture.failedFuture(IOException("Missing QQ music mid"))
        }
        synchronized(LOCK) {
            val cached = MEMORY_CACHE[mid]
            if (cached != null) return CompletableFuture.completedFuture(cached.duplicate())
            val existing = IN_FLIGHT[mid]
            if (existing != null) return existing
            val future = CompletableFuture.supplyAsync({
                try {
                    val bytes = loadFromDisk(mid) ?: QqMusicService.downloadAudioBytes(mid).also {
                        validate(it)
                        saveToDisk(mid, it)
                    }
                    val buffer = toDirectBuffer(bytes)
                    putInMemory(mid, buffer)
                    buffer.duplicate()
                } catch (exception: Exception) {
                    deleteCorruptFiles(mid)
                    throw RuntimeException(exception)
                }
            }, AcademyCraft.executorService).whenComplete { _, _ ->
                synchronized(LOCK) {
                    IN_FLIGHT.remove(mid)
                }
            }
            IN_FLIGHT[mid] = future
            return future
        }
    }

    private fun loadFromDisk(mid: String): ByteArray? {
        for (file in listOf(resolveFile(mid), resolveLegacyFile(mid))) {
            try {
                if (!Files.exists(file)) continue
                val bytes = Files.readAllBytes(file)
                validate(bytes)
                return bytes
            } catch (_: Exception) {
                AcademyCraft.LOGGER.warn("Corrupt QQ audio cache for {}, will re-download", mid)
                deleteFile(file)
            }
        }
        return null
    }

    @Throws(IOException::class)
    private fun validate(bytes: ByteArray?) {
        if (bytes == null || bytes.size < 4) throw IOException("Empty QQ audio response")
        val format = AudioFormatDetector.detect(bytes)
        if (!format.supported) {
            throw IOException("Unsupported QQ audio payload: $format")
        }
    }

    private fun saveToDisk(mid: String, bytes: ByteArray) {
        try {
            val file = resolveFile(mid)
            val temporary = file.resolveSibling("${file.fileName}.tmp")
            Files.createDirectories(file.parent)
            Files.write(temporary, bytes)
            Files.move(
                temporary, file,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (exception: Exception) {
            AcademyCraft.LOGGER.error("Failed to save QQ audio cache for {}", mid, exception)
        }
    }

    private fun deleteCorruptFiles(mid: String) {
        deleteFile(resolveFile(mid))
        deleteFile(resolveLegacyFile(mid))
    }

    private fun deleteFile(file: Path) {
        try {
            Files.deleteIfExists(file)
            Files.deleteIfExists(file.resolveSibling("${file.fileName}.tmp"))
        } catch (_: Exception) {
        }
    }

    private fun resolveFile(mid: String): Path = CACHE_DIR.resolve(sanitize(mid) + ".audio")

    private fun resolveLegacyFile(mid: String): Path = CACHE_DIR.resolve(sanitize(mid) + ".ogg")

    private fun putInMemory(mid: String, buffer: ByteBuffer) {
        synchronized(LOCK) {
            val previous = MEMORY_CACHE.remove(mid)
            if (previous != null) memoryBytes -= previous.capacity()
            MEMORY_CACHE[mid] = buffer
            memoryBytes += buffer.capacity()
            while (memoryBytes > MAX_IN_MEMORY_BYTES && MEMORY_CACHE.isNotEmpty()) {
                val eldest = MEMORY_CACHE.entries.iterator().next()
                MEMORY_CACHE.remove(eldest.key)
                memoryBytes -= eldest.value.capacity()
            }
            memoryBytes = memoryBytes.coerceAtLeast(0L)
        }
    }

    private fun toDirectBuffer(bytes: ByteArray): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(bytes.size)
        buffer.put(bytes).flip()
        return buffer
    }

    private fun sanitize(raw: String): String = raw
        .replace(':', '_')
        .replace('/', '_')
        .replace('\\', '_')
        .replace('.', '_')
        .replace(' ', '_')
}
