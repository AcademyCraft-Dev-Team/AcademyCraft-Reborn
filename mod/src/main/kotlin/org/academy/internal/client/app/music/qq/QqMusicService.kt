package org.academy.internal.client.app.music.qq

import org.academy.internal.common.music.provider.*
import java.io.IOException

/**
 * QQ 音乐客户端入口：以玩家本地凭证委托公共提供者实现喵。
 */
object QqMusicService {
    fun search(query: String): List<ProviderSearchResult> = provider().search(query, playerCredential())

    fun downloadAudioBytes(mid: String): ByteArray = provider().downloadTrack(mid, playerCredential())

    fun resolveAlbumMid(mid: String): String =
        (provider() as QqProvider).resolveAlbumMid(mid, playerCredential())

    fun downloadAlbumCoverBytes(albumMid: String?): ByteArray {
        if (albumMid.isNullOrBlank()) {
            throw IOException("Missing album mid for cover download")
        }
        val coverUrl = "https://y.qq.com/music/photo_new/T002R300x300M000$albumMid.jpg"
        return provider().downloadArtwork(coverUrl)
    }

    private fun provider(): MusicProviderApi = MusicProviders.qq()

    private fun playerCredential(): ProviderCredential {
        val credential = QqCredentialManager.getCredential()
        if (credential == null || credential.musicId.isBlank() || credential.musicKey.isBlank()) {
            return ProviderCredential.ANONYMOUS
        }
        val expiresAt = if (credential.keyExpiresIn > 0) {
            credential.musicKeyCreateTime + credential.keyExpiresIn
        } else {
            0L
        }
        return ProviderCredential.ofQq(credential.musicId, credential.musicKey, expiresAt)
    }
}
