package org.academy.internal.client.app.music.backend

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import org.academy.AcademyCraft
import org.academy.api.client.gui.state.UiState
import org.academy.internal.client.app.music.netease.NeteaseMusicService
import java.io.ByteArrayInputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier

/**
 * 账号头像下载缓存喵. 下载在后台线程, NativeImage/DynamicTexture 创建与
 * textureManager 注册全部切回渲染线程 (与 [AlbumArtworkCache] 一致的线程模型).
 */
object AccountAvatarCache {
    private val textures = ConcurrentHashMap<String, Identifier>()
    private val downloads = ConcurrentHashMap<String, CompletableFuture<*>>()

    /** 纹理注册完成时递增, 供控件订阅刷新 (事件驱动, 取代 tick 轮询). */
    val textureState = UiState(0)

    fun textureFor(provider: OnlineMusicManager.Provider, avatarUrl: String?): Identifier? {
        if (avatarUrl.isNullOrBlank()) return null
        val key = "${provider.storageName}_${avatarUrl.hashCode()}"
        textures[key]?.let { return it }
        if (downloads.putIfAbsent(key, CompletableFuture<Unit>()) != null) return null
        CompletableFuture
            .supplyAsync(
                { NeteaseMusicService.downloadAlbumCoverBytes(avatarUrl) },
                AcademyCraft.executorService
            )
            .thenAcceptAsync({ bytes -> registerTexture(key, bytes) }, Minecraft.getInstance())
            .whenComplete { _, throwable ->
                downloads.remove(key)
                if (throwable != null) {
                    AcademyCraft.LOGGER.warn(
                        "Failed to cache account avatar for {}:{}",
                        provider,
                        avatarUrl,
                        throwable
                    )
                }
            }
        return null
    }

    private fun registerTexture(key: String, bytes: ByteArray) {
        runCatching {
            val image = NativeImage.read(ByteArrayInputStream(bytes))
            val texture = DynamicTexture(Supplier { "academy_music_avatar_$key" }, image)
            val location = AcademyCraft.academy("music_avatar/$key")
            Minecraft.getInstance().textureManager.register(location, texture)
            textures[key] = location
            textureState.value = textureState.value + 1
        }.onFailure {
            AcademyCraft.LOGGER.error("Failed to register account avatar texture for {}", key, it)
        }
    }
}