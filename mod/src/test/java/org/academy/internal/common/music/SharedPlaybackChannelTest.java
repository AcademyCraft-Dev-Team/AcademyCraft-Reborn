package org.academy.internal.common.music;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SharedPlaybackChannelTest {
    private static SharedTrackEntry track(String id, int durationSeconds) {
        return new SharedTrackEntry("netease", id, "T" + id, "A", durationSeconds, false, "");
    }

    private static QueueSnapshot.QueueEntry entry(String id, int durationSeconds, String requester) {
        return new QueueSnapshot.QueueEntry(track(id, durationSeconds), requester);
    }

    @Test
    void playNowSetsPlayingTimeline() {
        var channel = new SharedPlaybackChannel();
        channel.playNow(entry("1", 60, "Steve"), 100L);
        assertTrue(channel.isPlaying());
        assertEquals("1", channel.currentEntry().orElseThrow().trackId());
        assertEquals(0f, channel.timeline().orElseThrow().expectedPositionSeconds(100L), 0.001f);
        assertEquals(5f, channel.timeline().orElseThrow().expectedPositionSeconds(200L), 0.001f);
    }

    @Test
    void advanceIfEndedOnlyAfterExpectedEnd() {
        var channel = new SharedPlaybackChannel();
        channel.playNow(entry("1", 60, "Steve"), 100L);
        channel.enqueue(entry("2", 30, "Alex"));

        assertFalse(channel.advanceIfEnded(100L + 60 * 20 - 1));
        assertTrue(channel.advanceIfEnded(100L + 60 * 20));

        assertEquals("2", channel.currentEntry().orElseThrow().trackId());
        assertTrue(channel.isPlaying());
    }

    @Test
    void advanceWithoutQueueClearsTimeline() {
        var channel = new SharedPlaybackChannel();
        channel.playNow(entry("1", 10, "Steve"), 0L);
        assertTrue(channel.advanceIfEnded(10 * 20 + 1));
        assertFalse(channel.timeline().isPresent());
        assertFalse(channel.isPlaying());
    }

    @Test
    void pausedTimelineDoesNotAdvanceOrEnd() {
        var channel = new SharedPlaybackChannel();
        channel.playNow(entry("1", 60, "Steve"), 100L);
        channel.pause(200L);
        assertFalse(channel.isPlaying());
        // 暂停期间不应推进
        assertFalse(channel.advanceIfEnded(100_000L));
        assertEquals(5f, channel.timeline().orElseThrow().expectedPositionSeconds(100_000L), 0.001f);

        channel.resume(300L);
        assertTrue(channel.isPlaying());
        assertEquals(5f, channel.timeline().orElseThrow().expectedPositionSeconds(300L), 0.001f);
    }

    @Test
    void unknownDurationTimelineNeverAutoEnds() {
        var channel = new SharedPlaybackChannel();
        channel.playNow(entry("1", 0, "Steve"), 0L);
        assertFalse(channel.advanceIfEnded(Long.MAX_VALUE / 2));
    }

    @Test
    void skipToPreviousRestoresHistoryAndPushesCurrentToQueueFront() {
        var channel = new SharedPlaybackChannel();
        channel.playNow(entry("1", 60, "Steve"), 100L);
        channel.enqueue(entry("2", 60, "Alex"));
        channel.skipToNext(200L);
        assertEquals("2", channel.currentEntry().orElseThrow().trackId());

        assertTrue(channel.skipToPrevious(300L));
        assertEquals("1", channel.currentEntry().orElseThrow().trackId());
        assertEquals("2", channel.queueSnapshot().entries().get(0).entry().trackId());
    }

    @Test
    void skipToPreviousWithoutHistoryRestartsCurrentFromZero() {
        var channel = new SharedPlaybackChannel();
        channel.playNow(entry("9", 60, "Bob"), 500L);
        channel.seek(600L, 30f);
        assertEquals(30f, channel.timeline().orElseThrow().expectedPositionSeconds(600L), 0.001f);

        assertTrue(channel.skipToPrevious(700L));
        assertEquals("9", channel.currentEntry().orElseThrow().trackId());
        assertEquals(0f, channel.timeline().orElseThrow().expectedPositionSeconds(700L), 0.001f);
    }

    @Test
    void queueRemoveByIndex() {
        var channel = new SharedPlaybackChannel();
        channel.enqueue(entry("1", 10, "A"));
        channel.enqueue(entry("2", 10, "B"));
        channel.enqueue(entry("3", 10, "C"));

        assertTrue(channel.removeQueueIndex(1));
        var entries = channel.queueSnapshot().entries();
        assertEquals(2, entries.size());
        assertEquals("1", entries.get(0).entry().trackId());
        assertEquals("3", entries.get(1).entry().trackId());
        assertFalse(channel.removeQueueIndex(5));
    }

    @Test
    void seekKeepsPositionClamped() {
        var channel = new SharedPlaybackChannel();
        channel.playNow(entry("1", 60, "Steve"), 100L);
        channel.seek(200L, 42f);
        assertEquals(42f, channel.timeline().orElseThrow().expectedPositionSeconds(200L), 0.001f);
    }

    @Test
    void requesterTrackedAcrossAdvances() {
        var channel = new SharedPlaybackChannel();
        channel.playNow(entry("1", 10, "Steve"), 0L);
        channel.enqueue(entry("2", 10, "Alex"));
        channel.advanceIfEnded(10 * 20);
        assertEquals("Alex", channel.currentQueueEntry().orElseThrow().requesterName());
    }

    @Test
    void restoreReinstatesTimelineQueueAndRequester() {
        var channel = new SharedPlaybackChannel();
        var restoredTimeline = PlaybackTimeline.pausedAt(track("7", 120), 500L, 33f);
        channel.restore(restoredTimeline, "Steve", List.of(entry("8", 60, "Alex")));

        assertEquals("7", channel.currentEntry().orElseThrow().trackId());
        assertFalse(channel.isPlaying());
        assertEquals(33f, channel.timeline().orElseThrow().expectedPositionSeconds(99999L), 0.001f);
        assertEquals("Steve", channel.currentQueueEntry().orElseThrow().requesterName());
        assertEquals(1, channel.queueSize());
        assertEquals("8", channel.queueSnapshot().entries().get(0).entry().trackId());
    }

    @Test
    void restoreWithNullTimelineKeepsQueueOnly() {
        var channel = new SharedPlaybackChannel();
        channel.restore(null, "", List.of(entry("1", 10, "A"), entry("2", 10, "B")));

        assertFalse(channel.timeline().isPresent());
        assertFalse(channel.isPlaying());
        assertEquals(2, channel.queueSize());
    }

    @Test
    void restoreIsIdempotentAndSkipsNullEntries() {
        var channel = new SharedPlaybackChannel();
        channel.playNow(entry("old", 10, "X"), 0L);
        channel.enqueue(entry("stale", 10, "Y"));

        channel.restore(PlaybackTimeline.pausedAt(track("new", 60), 100L, 5f), "Z",
                Arrays.asList(entry("a", 10, "A"), null));

        assertEquals("new", channel.currentEntry().orElseThrow().trackId());
        assertEquals(1, channel.queueSize());
        assertEquals("a", channel.queueSnapshot().entries().get(0).entry().trackId());
    }

    @Test
    void skipToNextOnIdleChannelStartsQueuedTrack() {
        // 房间空闲时加入队列即开播的核心保障: 无时间线也要能推进到队首喵.
        var channel = new SharedPlaybackChannel();
        channel.enqueue(entry("queued", 90, "Adder"));
        assertFalse(channel.timeline().isPresent());

        assertTrue(channel.skipToNext(500L));
        assertTrue(channel.isPlaying());
        assertEquals("queued", channel.currentEntry().orElseThrow().trackId());
        assertEquals("Adder", channel.currentQueueEntry().orElseThrow().requesterName());
        assertTrue(channel.queueSnapshot().entries().isEmpty(), "The started track must leave the queue");
    }

    @Test
    void skipToNextOnEmptyIdleChannelReportsNoChange() {
        var channel = new SharedPlaybackChannel();
        assertFalse(channel.skipToNext(500L), "Nothing to advance when idle with an empty queue");
        assertFalse(channel.isPlaying());
    }

    @Test
    void changeListenerFiresOnTimelineAndQueueChanges() {
        var channel = new SharedPlaybackChannel();
        var kinds = new ArrayList<SharedPlaybackChannel.ChangeKind>();
        channel.setChangeListener(kinds::add);

        channel.playNow(entry("1", 10, "S"), 0L);
        channel.enqueue(entry("2", 10, "A"));
        channel.skipToNext(100L);

        assertEquals(
                List.of(
                        SharedPlaybackChannel.ChangeKind.TIMELINE,
                        SharedPlaybackChannel.ChangeKind.QUEUE,
                        SharedPlaybackChannel.ChangeKind.TIMELINE
                ),
                kinds
        );
    }
}
