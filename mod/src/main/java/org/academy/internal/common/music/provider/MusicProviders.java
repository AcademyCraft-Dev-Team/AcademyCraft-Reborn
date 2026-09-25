package org.academy.internal.common.music.provider;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 提供者注册表：按 storage 名（"qq" / "netease"）解析实现，客户端与服务端共用喵。
 */
public final class MusicProviders {
    private static final Map<String, MusicProviderApi> PROVIDERS = Map.of(
            QqProvider.NAME, new QqProvider(),
            NeteaseProvider.NAME, new NeteaseProvider()
    );

    private MusicProviders() {
    }

    public static Optional<MusicProviderApi> byName(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(PROVIDERS.get(name.toLowerCase(Locale.ROOT)));
    }

    public static MusicProviderApi qq() {
        return byName(QqProvider.NAME).orElseThrow();
    }

    public static MusicProviderApi netease() {
        return byName(NeteaseProvider.NAME).orElseThrow();
    }

    /**
     * 按顺序尝试候选直链，全部失败时抛出携带各次失败原因（suppressed）的异常喵。
     */
    public static byte[] downloadFirstSupported(List<String> streamUrls, TrackDownloader downloader) throws IOException {
        IOException failure = new IOException("No downloadable supported audio source");
        for (var streamUrl : streamUrls) {
            try {
                return downloader.download(streamUrl);
            } catch (IOException exception) {
                failure.addSuppressed(exception);
            }
        }
        throw failure;
    }

    @FunctionalInterface
    public interface TrackDownloader {
        byte[] download(String streamUrl) throws IOException;
    }
}
