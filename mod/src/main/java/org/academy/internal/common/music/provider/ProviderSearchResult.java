package org.academy.internal.common.music.provider;

/**
 * 提供者无关的搜索结果，客户端与服务端共用喵。
 */
public record ProviderSearchResult(
        String trackId,
        String title,
        String artist,
        int durationSeconds,
        boolean vip,
        String artworkUrl
) {
    public ProviderSearchResult {
        trackId = trackId == null ? "" : trackId;
        title = title == null ? "" : title;
        artist = artist == null ? "" : artist;
        artworkUrl = artworkUrl == null ? "" : artworkUrl;
        durationSeconds = Math.max(0, durationSeconds);
    }

    public String displayText() {
        if (title.isBlank()) {
            return artist.isBlank() ? "" : artist;
        }
        return artist.isBlank() ? title : title + " - " + artist;
    }
}
