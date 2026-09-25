package org.academy.internal.common.music;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlaybackTimelineTest {
    private static SharedTrackEntry track(int durationSeconds) {
        return new SharedTrackEntry("netease", "12345", "Title", "Artist", durationSeconds, false, "https://example.com/a.jpg");
    }

    @Test
    void playingAdvancesWithGameTime() {
        var timeline = PlaybackTimeline.startedAt(track(200), 1000L);
        assertEquals(0f, timeline.expectedPositionSeconds(1000L), 0.001f);
        assertEquals(5f, timeline.expectedPositionSeconds(1100L), 0.001f);
    }

    @Test
    void pausedDoesNotAdvance() {
        var timeline = PlaybackTimeline.pausedAt(track(200), 1000L, 42f);
        assertEquals(42f, timeline.expectedPositionSeconds(9999L), 0.001f);
    }

    @Test
    void gameTimeBeforeAnchorClampedToAnchorPosition() {
        var timeline = PlaybackTimeline.startedAt(track(200), 1000L);
        assertEquals(0f, timeline.expectedPositionSeconds(500L), 0.001f);
    }

    @Test
    void withPlayingReanchorsPosition() {
        var timeline = PlaybackTimeline.startedAt(track(200), 1000L);
        var paused = timeline.withPlaying(1500L, false);
        assertFalse(paused.playing());
        assertEquals(25f, paused.expectedPositionSeconds(1500L), 0.001f);
        assertEquals(25f, paused.expectedPositionSeconds(99999L), 0.001f);

        var resumed = paused.withPlaying(2000L, true);
        assertTrue(resumed.playing());
        assertEquals(25f, resumed.anchorPositionSeconds(), 0.001f);
        assertEquals(50f, resumed.expectedPositionSeconds(2500L), 0.001f);
    }

    @Test
    void seekKeepsPlayingStateAndFreezesWhenPaused() {
        var timeline = PlaybackTimeline.startedAt(track(200), 1000L);
        var sought = timeline.seekTo(1600L, 60f).withPlaying(1600L, false);
        assertFalse(sought.playing());
        assertEquals(60f, sought.expectedPositionSeconds(99999L), 0.001f);
    }

    @Test
    void expectedEndComputedFromRemaining() {
        var timeline = PlaybackTimeline.startedAt(track(60), 1000L);
        assertEquals(1000L + 60 * 20, timeline.expectedEndGameTime());

        var resumedMidway = PlaybackTimeline.pausedAt(track(60), 1000L, 30f).withPlaying(1100L, true);
        assertEquals(1100L + 30 * 20, resumedMidway.expectedEndGameTime());
    }

    @Test
    void unknownDurationNeverEnds() {
        var timeline = PlaybackTimeline.startedAt(track(0), 1000L);
        assertEquals(Long.MAX_VALUE, timeline.expectedEndGameTime());
    }

    @Test
    void reanchorFoldsProgressIntoTimeline() {
        var timeline = PlaybackTimeline.startedAt(track(200), 1000L);
        // 起播后 10 秒广播：重锚后锚点就是 10 秒，接收端直接以它为基准喵.
        var reanchored = timeline.reanchored(1200L);
        assertTrue(reanchored.playing());
        assertEquals(1200L, reanchored.anchorGameTime());
        assertEquals(10f, reanchored.anchorPositionSeconds(), 0.001f);
        assertEquals(10f, reanchored.expectedPositionSeconds(1200L), 0.001f);
        assertEquals(15f, reanchored.expectedPositionSeconds(1300L), 0.001f);
    }

    @Test
    void reanchorOfPausedTimelineKeepsPosition() {
        var timeline = PlaybackTimeline.pausedAt(track(200), 1000L, 42f);
        var reanchored = timeline.reanchored(5000L);
        assertFalse(reanchored.playing());
        assertEquals(42f, reanchored.anchorPositionSeconds(), 0.001f);
        assertEquals(42f, reanchored.expectedPositionSeconds(99999L), 0.001f);
    }

    @Test
    void codecRoundTrip() {
        var timeline = PlaybackTimeline.pausedAt(track(200), 12345L, 67.5f);
        ByteBuf buf = Unpooled.buffer();
        PlaybackTimeline.CODEC.encode(buf, timeline);
        var decoded = PlaybackTimeline.CODEC.decode(buf);
        assertEquals(timeline, decoded);
        buf.release();
    }

    @Test
    void nullsAreSanitized() {
        var entry = new SharedTrackEntry(null, null, null, null, -5, false, null);
        assertEquals("", entry.provider());
        assertEquals("", entry.trackId());
        assertEquals(0, entry.durationSeconds());
        assertEquals("Title - Artist", track(1).displayText());
    }
}
