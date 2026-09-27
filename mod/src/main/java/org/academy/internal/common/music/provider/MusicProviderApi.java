package org.academy.internal.common.music.provider;

import org.academy.internal.common.music.SharedTrackEntry;

import java.io.IOException;
import java.util.List;

/**
 * 音乐提供者服务的公共接口：客户端本地播放与服务端共享VIP解析共用同一套实现，
 * 通过 {@link ProviderCredential} 区分登录身份。新增音源时实现本接口并注册到 {@link MusicProviders} 喵。
 */
public interface MusicProviderApi {
    String name();

    List<ProviderSearchResult> search(String query, ProviderCredential credential) throws IOException;

    /**
     * 解析曲目的可用 CDN 直链列表；无可播放源时抛出 IOException 并附带面向玩家的诊断信息。
     */
    TrackCandidate resolveTrack(String trackId, ProviderCredential credential) throws IOException;

    default byte[] downloadTrack(String trackId, ProviderCredential credential) throws IOException {
        var candidate = resolveTrack(trackId, credential);
        try {
            return MusicProviders.downloadFirstSupported(candidate.streamUrls(), streamUrl -> {
                var bytes = HttpUtil.downloadBytes(
                        streamUrl,
                        userAgent(),
                        referer(),
                        15000,
                        30000
                );
                var format = AudioFormat.detect(bytes);
                if (!format.isSupported()) {
                    throw new IOException("Provider returned unsupported audio format " + format);
                }
                return bytes;
            });
        } catch (IOException failure) {
            if (!candidate.diagnoseMessage().isBlank()) {
                throw new IOException(candidate.diagnoseMessage(), failure);
            }
            throw failure;
        }
    }

    default byte[] downloadArtwork(String url) throws IOException {
        return HttpUtil.downloadBytes(url, userAgent(), referer(), 10000, 20000);
    }

    /**
     * 拉取提供者歌单详情（服务器预设歌单导入用）；不支持歌单导入的音源抛 IOException 喵。
     */
    default List<SharedTrackEntry> importPlaylist(
            String playlistId,
            ProviderCredential credential
    ) throws IOException {
        throw new IOException("Provider " + name() + " does not support playlist import");
    }

    default String userAgent() {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0";
    }

    default String referer() {
        return "";
    }
}
