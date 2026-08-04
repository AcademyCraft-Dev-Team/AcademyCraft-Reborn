package org.academy.internal.client.app.music.backend

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import net.neoforged.fml.loading.FMLPaths
import org.academy.AcademyCraft
import org.academy.internal.client.app.music.data.MusicInfo
import org.academy.internal.client.app.music.netease.NeteaseMusicService
import org.academy.internal.client.app.music.qq.QqMusicService
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier
import javax.imageio.ImageIO

object AlbumArtworkCache {
    private const val RETRY_DELAY_MS = 60_000L
    private val cacheDirectory: Path = FMLPaths.GAMEDIR.get()
        .resolve("academy_music")
        .resolve("album_covers")
    private val textures = ConcurrentHashMap<String, Identifier>()
    private val downloads = ConcurrentHashMap<String, CompletableFuture<*>>()
    private val retryAfter = ConcurrentHashMap<String, Long>()

    init {
        ensureCacheDirectory()
    }

    fun init() {
        ensureCacheDirectory()
    }

    fun textureFor(info: MusicInfo): Identifier {
        if (info.provider == "local" || info.externalId.isBlank()) return info.icon
        val key = cacheKey(info)
        textures[key]?.let { return it }
        request(info, key)
        return info.icon
    }

    private fun request(info: MusicInfo, key: String) {
        if (System.currentTimeMillis() < retryAfter.getOrDefault(key, 0L)) return
        val pending = CompletableFuture<Unit>()
        if (downloads.putIfAbsent(key, pending) != null) return
        CompletableFuture.supplyAsync({ loadOrDownload(info, key) }, AcademyCraft.executorService)
            .thenAcceptAsync({ bytes -> registerTexture(key, bytes) }, Minecraft.getInstance())
            .whenComplete { _, throwable ->
                downloads.remove(key, pending)
                if (throwable != null) {
                    pending.completeExceptionally(throwable)
                    retryAfter[key] = System.currentTimeMillis() + RETRY_DELAY_MS
                    AcademyCraft.LOGGER.warn("Failed to cache album artwork for {}:{}", info.provider, info.externalId, throwable)
                } else {
                    pending.complete(Unit)
                    retryAfter.remove(key)
                }
            }
    }

    private fun loadOrDownload(info: MusicInfo, key: String): ByteArray {
        Files.createDirectories(cacheDirectory)
        val cacheFile = cacheDirectory.resolve("$key.png")
        if (Files.exists(cacheFile)) {
            val cached = Files.readAllBytes(cacheFile)
            if (cached.isNotEmpty() && isValidImage(cached)) return cached
            Files.deleteIfExists(cacheFile)
        }

        val legacyCacheFile = cacheDirectory.resolve("$key.img")
        if (Files.exists(legacyCacheFile)) {
            val cached = runCatching { normalizeToPng(Files.readAllBytes(legacyCacheFile)) }.getOrNull()
            if (cached != null && isValidImage(cached)) {
                Files.write(cacheFile, cached)
                Files.deleteIfExists(legacyCacheFile)
                return cached
            }
            Files.deleteIfExists(legacyCacheFile)
        }

        val downloaded = when (info.provider.lowercase()) {
            OnlineMusicManager.Provider.QQ.storageName -> {
                val albumMid = QqMusicService.resolveAlbumMid(info.externalId)
                QqMusicService.downloadAlbumCoverBytes(albumMid)
            }

            OnlineMusicManager.Provider.NETEASE.storageName -> {
                NeteaseMusicService.downloadAlbumCoverBytes(info.artworkUrl)
            }

            else -> error("Unsupported online music provider: ${info.provider}")
        }
        require(downloaded.isNotEmpty()) { "Downloaded album artwork is empty" }
        val normalized = normalizeToPng(downloaded)
        require(isValidImage(normalized)) { "Downloaded album artwork is invalid" }
        Files.write(cacheFile, normalized)
        return normalized
    }

    private fun registerTexture(key: String, bytes: ByteArray) {
        val image = NativeImage.read(ByteArrayInputStream(bytes))
        val texture = DynamicTexture(Supplier { "academy_music_cover_$key" }, image)
        val location = AcademyCraft.academy("music_cover/$key")
        Minecraft.getInstance().textureManager.register(location, texture)
        textures[key] = location
    }

    private fun isValidImage(bytes: ByteArray): Boolean {
        return runCatching {
            NativeImage.read(ByteArrayInputStream(bytes)).use { image ->
                image.width > 0 && image.height > 0
            }
        }.getOrDefault(false)
    }

    private fun normalizeToPng(bytes: ByteArray): ByteArray {
        val image = ImageIO.read(ByteArrayInputStream(bytes))
            ?: throw IllegalArgumentException("Downloaded album artwork is not a supported image")
        return ByteArrayOutputStream().use { output ->
            require(ImageIO.write(image, "png", output)) { "Unable to encode album artwork as PNG" }
            output.toByteArray()
        }
    }

    private fun cacheKey(info: MusicInfo): String {
        val provider = sanitize(info.provider)
        val track = sanitize(info.externalId)
        return "${provider}_$track"
    }

    private fun sanitize(value: String): String {
        return value.lowercase().replace(Regex("[^a-z0-9._-]"), "_")
    }

    private fun ensureCacheDirectory() {
        runCatching { Files.createDirectories(cacheDirectory) }
            .onFailure { AcademyCraft.LOGGER.warn("Failed to create album artwork cache directory {}", cacheDirectory, it) }
    }
}
