package org.academy.internal.client.app.music.netease

import net.neoforged.fml.loading.FMLPaths
import org.academy.AcademyCraft
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.CompletableFuture

object NeteaseAudioCache {
    private const val MAX_IN_MEMORY_BYTES = 64L * 1024L * 1024L
    private val CACHE_DIR: Path by lazy {
        FMLPaths.GAMEDIR.get().resolve("academy_music").resolve("netease_cache")
    }
    private val LOCK = Any()
    private val MEMORY_CACHE = LinkedHashMap<String, ByteBuffer>(16, 0.75f, true)
    private val IN_FLIGHT = LinkedHashMap<String, CompletableFuture<ByteBuffer>>()
    private var memoryBytes = 0L

    fun ensureCachedAsync(songId: String?): CompletableFuture<ByteBuffer> {
        if (songId.isNullOrBlank()) {
            return CompletableFuture.failedFuture(IOException("Missing NetEase song id"))
        }
        synchronized(LOCK) {
            val cached = MEMORY_CACHE[songId]
            if (cached != null) return CompletableFuture.completedFuture(cached.duplicate())
            val existing = IN_FLIGHT[songId]
            if (existing != null) return existing
            val future = CompletableFuture.supplyAsync({
                try {
                    val bytes = loadFromDisk(songId) ?: NeteaseMusicService.downloadStreamBytes(songId).also {
                        validate(it)
                        saveToDisk(songId, it)
                    }
                    val buffer = toDirectBuffer(bytes)
                    putInMemory(songId, buffer)
                    buffer.duplicate()
                } catch (exception: Exception) {
                    deleteCorruptFile(resolveFile(songId))
                    throw RuntimeException(exception)
                }
            }, AcademyCraft.executorService).whenComplete { _, _ ->
                synchronized(LOCK) {
                    IN_FLIGHT.remove(songId)
                }
            }
            IN_FLIGHT[songId] = future
            return future
        }
    }

    /**
     * 收录服务器共享账号解析得到的音频（校验后落盘并入内存缓存）。
     * 同步执行，避免在双线程池上嵌套 join 造成死锁喵。
     */
    fun acceptServerResolved(songId: String, bytes: ByteArray): CompletableFuture<ByteBuffer> {
        return try {
            validate(bytes)
            saveToDisk(songId, bytes)
            val buffer = toDirectBuffer(bytes)
            putInMemory(songId, buffer)
            CompletableFuture.completedFuture(buffer.duplicate())
        } catch (exception: Exception) {
            CompletableFuture.failedFuture(exception)
        }
    }

    private fun loadFromDisk(songId: String): ByteArray? {
        try {
            val file = resolveFile(songId)
            if (!Files.exists(file)) return null
            val bytes = Files.readAllBytes(file)
            validate(bytes)
            return bytes
        } catch (_: Exception) {
            AcademyCraft.LOGGER.warn("Corrupt NetEase audio cache for {}, will re-download", songId)
            return null
        }
    }

    @Throws(IOException::class)
    private fun validate(bytes: ByteArray?) {
        if (bytes == null || bytes.size < 4) throw IOException("Empty NetEase audio response")
        val id3 = bytes[0] == 'I'.code.toByte() && bytes[1] == 'D'.code.toByte() && bytes[2] == '3'.code.toByte()
        val mp3Frame = (bytes[0].toInt() and 0xff) == 0xff && (bytes[1].toInt() and 0xe0) == 0xe0
        val flac = bytes[0] == 'f'.code.toByte() && bytes[1] == 'L'.code.toByte() &&
                bytes[2] == 'a'.code.toByte() && bytes[3] == 'C'.code.toByte()
        if (!id3 && !mp3Frame && !flac) throw IOException("Unsupported NetEase audio payload")
    }

    private fun saveToDisk(songId: String, bytes: ByteArray) {
        try {
            val file = resolveFile(songId)
            val temporary = file.resolveSibling("${file.fileName}.tmp")
            Files.createDirectories(file.parent)
            Files.write(temporary, bytes)
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (exception: Exception) {
            AcademyCraft.LOGGER.warn("Failed to save NetEase audio cache for {}", songId, exception)
        }
    }

    private fun deleteCorruptFile(file: Path) {
        try {
            Files.deleteIfExists(file)
            Files.deleteIfExists(file.resolveSibling("${file.fileName}.tmp"))
        } catch (_: Exception) {
        }
    }

    private fun resolveFile(songId: String): Path =
        CACHE_DIR.resolve(songId.replace(Regex("[^a-zA-Z0-9_-]"), "_") + ".mp3")

    private fun toDirectBuffer(bytes: ByteArray): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(bytes.size)
        buffer.put(bytes).flip()
        return buffer
    }

    private fun putInMemory(songId: String, buffer: ByteBuffer) {
        synchronized(LOCK) {
            val previous = MEMORY_CACHE.remove(songId)
            if (previous != null) memoryBytes -= previous.capacity()
            MEMORY_CACHE[songId] = buffer
            memoryBytes += buffer.capacity()
            while (memoryBytes > MAX_IN_MEMORY_BYTES && MEMORY_CACHE.isNotEmpty()) {
                val eldest = MEMORY_CACHE.entries.iterator().next()
                MEMORY_CACHE.remove(eldest.key)
                memoryBytes -= eldest.value.capacity()
            }
        }
    }
}
