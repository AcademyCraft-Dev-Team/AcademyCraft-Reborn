package org.academy.internal.common.music;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * 在线音乐曲目的通用描述，客户端与服务端共用，是共享播放（音乐室/全服点播）的最小元数据单位喵。
 */
public record SharedTrackEntry(
        String provider,
        String trackId,
        String title,
        String artist,
        int durationSeconds,
        boolean vip,
        String artworkUrl
) {
    public SharedTrackEntry {
        provider = safe(provider);
        trackId = safe(trackId);
        title = safe(title);
        artist = safe(artist);
        artworkUrl = safe(artworkUrl);
        durationSeconds = Math.max(0, durationSeconds);
    }

    public static final StreamCodec<ByteBuf, SharedTrackEntry> CODEC = StreamCodec.of(
            (buf, entry) -> {
                ByteBufCodecs.STRING_UTF8.encode(buf, entry.provider);
                ByteBufCodecs.STRING_UTF8.encode(buf, entry.trackId);
                ByteBufCodecs.STRING_UTF8.encode(buf, entry.title);
                ByteBufCodecs.STRING_UTF8.encode(buf, entry.artist);
                ByteBufCodecs.VAR_INT.encode(buf, entry.durationSeconds);
                ByteBufCodecs.BOOL.encode(buf, entry.vip);
                ByteBufCodecs.STRING_UTF8.encode(buf, entry.artworkUrl);
            },
            buf -> new SharedTrackEntry(
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf)
            )
    );

    public String displayText() {
        if (title.isBlank()) {
            return artist.isBlank() ? "" : artist;
        }
        return artist.isBlank() ? title : title + " - " + artist;
    }

    public boolean sameTrack(String otherProvider, String otherTrackId) {
        return provider.equals(otherProvider) && trackId.equals(otherTrackId);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
