package org.academy.internal.client.app.music.netease;

import org.academy.internal.common.music.provider.MusicProviderApi;
import org.academy.internal.common.music.provider.MusicProviders;
import org.academy.internal.common.music.provider.ProviderCredential;
import org.academy.internal.common.music.provider.ProviderSearchResult;

import java.io.IOException;
import java.util.List;

/**
 * 网易云音乐客户端入口：以玩家本地凭证委托公共提供者实现喵。
 */
public final class NeteaseMusicService {
    private NeteaseMusicService() {
    }

    public static List<ProviderSearchResult> search(String query) throws IOException {
        return provider().search(query, playerCredential());
    }

    public static String resolveStreamUrl(String songId) throws IOException {
        var candidate = provider().resolveTrack(songId, playerCredential());
        return candidate.streamUrls().isEmpty() ? "" : candidate.streamUrls().get(0);
    }

    public static byte[] downloadStreamBytes(String songId) throws IOException {
        return provider().downloadTrack(songId, playerCredential());
    }

    public static byte[] downloadAlbumCoverBytes(String picUrl) throws IOException {
        return provider().downloadArtwork(picUrl);
    }

    static MusicProviderApi provider() {
        return MusicProviders.netease();
    }

    static ProviderCredential playerCredential() {
        return ProviderCredential.fromCookieSupplier(NeteaseCredentialManager::getEffectiveCookie);
    }
}
