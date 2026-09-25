package org.academy.internal.client.app.music.netease

import org.academy.internal.common.music.provider.MusicProviderApi
import org.academy.internal.common.music.provider.MusicProviders
import org.academy.internal.common.music.provider.ProviderCredential
import org.academy.internal.common.music.provider.ProviderSearchResult

/**
 * 网易云音乐客户端入口：以玩家本地凭证委托公共提供者实现喵。
 */
object NeteaseMusicService {
    fun search(query: String): List<ProviderSearchResult> = provider().search(query, playerCredential())

    fun downloadStreamBytes(songId: String): ByteArray = provider().downloadTrack(songId, playerCredential())

    fun downloadAlbumCoverBytes(picUrl: String): ByteArray = provider().downloadArtwork(picUrl)

    private fun provider(): MusicProviderApi = MusicProviders.netease()

    private fun playerCredential(): ProviderCredential =
        ProviderCredential.fromCookieSupplier(NeteaseCredentialManager::getEffectiveCookie)
}
