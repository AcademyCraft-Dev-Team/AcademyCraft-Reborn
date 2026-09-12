package org.academy.internal.common.music;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Objects;

/**
 * 服务端权威的虚拟播放轴：锚定 (服务器tick, 曲内秒)，客户端据此推算当前进度并对齐喵。
 */
public record PlaybackTimeline(
        SharedTrackEntry entry,
        boolean playing,
        long anchorGameTime,
        float anchorPositionSeconds
) {
    public static final int TICKS_PER_SECOND = 20;

    public PlaybackTimeline {
        Objects.requireNonNull(entry, "entry");
        anchorGameTime = Math.max(0, anchorGameTime);
        anchorPositionSeconds = Float.isFinite(anchorPositionSeconds)
                ? Math.max(0.0f, anchorPositionSeconds)
                : 0.0f;
    }

    public static PlaybackTimeline startedAt(SharedTrackEntry entry, long gameTime) {
        return new PlaybackTimeline(entry, true, gameTime, 0.0f);
    }

    public static PlaybackTimeline resumedAt(SharedTrackEntry entry, long gameTime, float positionSeconds) {
        return new PlaybackTimeline(entry, true, gameTime, positionSeconds);
    }

    public static PlaybackTimeline pausedAt(SharedTrackEntry entry, long gameTime, float positionSeconds) {
        return new PlaybackTimeline(entry, false, gameTime, positionSeconds);
    }

    public float expectedPositionSeconds(long nowGameTime) {
        if (!playing) return anchorPositionSeconds;
        var elapsedSeconds = Math.max(0, nowGameTime - anchorGameTime) / (float) TICKS_PER_SECOND;
        return Math.max(0.0f, anchorPositionSeconds + elapsedSeconds);
    }

    /**
     * @return 预计播完的服务器tick；时长未知时返回 {@link Long#MAX_VALUE}。暂停期间进度不推进，由调用方保证暂停时不判定结束喵。
     */
    public long expectedEndGameTime() {
        var duration = entry.durationSeconds();
        if (duration <= 0) return Long.MAX_VALUE;
        var remaining = Math.max(0.0f, duration - anchorPositionSeconds);
        return anchorGameTime + Math.round(remaining * TICKS_PER_SECOND);
    }

    public PlaybackTimeline seekTo(long gameTime, float positionSeconds) {
        var clamped = Math.max(0.0f, positionSeconds);
        return new PlaybackTimeline(entry, playing, gameTime, clamped);
    }

    public PlaybackTimeline withPlaying(long gameTime, boolean nowPlaying) {
        var position = expectedPositionSeconds(gameTime);
        if (nowPlaying == playing) {
            return new PlaybackTimeline(entry, playing, gameTime, position);
        }
        return nowPlaying
                ? resumedAt(entry, gameTime, position)
                : pausedAt(entry, gameTime, position);
    }

    /**
     * 以给定服务器tick重新锚定：保留播放态，把进度折算到当前时刻。
     * 广播前重锚可让每个接收端直接以 anchorPositionSeconds 为基准，
     * 不依赖其本地接收时刻推算，避免中途加入的曲目被误算成 0 秒从头播放喵。
     */
    public PlaybackTimeline reanchored(long gameTime) {
        return withPlaying(gameTime, playing);
    }

    public static final StreamCodec<ByteBuf, PlaybackTimeline> CODEC = StreamCodec.of(
            (buf, timeline) -> {
                SharedTrackEntry.CODEC.encode(buf, timeline.entry);
                ByteBufCodecs.BOOL.encode(buf, timeline.playing);
                ByteBufCodecs.VAR_LONG.encode(buf, timeline.anchorGameTime);
                ByteBufCodecs.FLOAT.encode(buf, timeline.anchorPositionSeconds);
            },
            buf -> new PlaybackTimeline(
                    SharedTrackEntry.CODEC.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf)
            )
    );
}
