package org.academy.internal.client.app.music.qq;

import org.academy.internal.common.music.provider.MusicProviderApi;
import org.academy.internal.common.music.provider.MusicProviders;
import org.academy.internal.common.music.provider.ProviderCredential;
import org.academy.internal.common.music.provider.ProviderSearchResult;
import org.academy.internal.common.music.provider.QqProvider;

import java.io.IOException;
import java.util.List;

/**
 * QQ 音乐客户端入口：以玩家本地凭证委托公共提供者实现喵。
 */
public final class QqMusicService {
    private QqMusicService() {
    }

    public static List<ProviderSearchResult> search(String query) throws IOException {
        return provider().search(query, playerCredential());
    }

    public static byte[] downloadAudioBytes(String mid) throws IOException {
        return provider().downloadTrack(mid, playerCredential());
    }

    public static String resolveAlbumMid(String mid) throws IOException {
        return ((QqProvider) provider()).resolveAlbumMid(mid, playerCredential());
    }

    public static byte[] downloadAlbumCoverBytes(String albumMid) throws IOException {
        if (albumMid == null || albumMid.isBlank()) {
            throw new IOException("Missing album mid for cover download");
        }
        var coverUrl = "https://y.qq.com/music/photo_new/T002R300x300M000" + albumMid + ".jpg";
        return provider().downloadArtwork(coverUrl);
    }

    static MusicProviderApi provider() {
        return MusicProviders.qq();
    }

    static ProviderCredential playerCredential() {
        var credential = QqCredentialManager.getCredential();
        if (credential == null
                || credential.getMusicId().isBlank()
                || credential.getMusicKey().isBlank()) {
            return ProviderCredential.ANONYMOUS;
        }
        var expiresAt = credential.getKeyExpiresIn() > 0
                ? credential.getMusicKeyCreateTime() + credential.getKeyExpiresIn()
                : 0L;
        return ProviderCredential.ofQq(credential.getMusicId(), credential.getMusicKey(), expiresAt);
    }
}
