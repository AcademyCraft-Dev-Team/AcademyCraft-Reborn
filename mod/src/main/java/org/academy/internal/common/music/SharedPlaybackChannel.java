package org.academy.internal.common.music;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 服务端共享播放通道的通用骨架：队列 + 权威时间线 + 自动切歌推进。
 * 音乐室与全服点播复用同一套推进逻辑，状态变化通过 {@link #setChangeListener} 通知子类广播喵。
 */
public class SharedPlaybackChannel {
    private static final int HISTORY_LIMIT = 16;

    private final ArrayDeque<QueueSnapshot.QueueEntry> queue = new ArrayDeque<>();
    private final ArrayDeque<QueueSnapshot.QueueEntry> history = new ArrayDeque<>();
    private PlaybackTimeline timeline;
    private String currentRequester = "";
    private int revision;
    private Consumer<ChangeKind> changeListener = change -> {
    };

    public enum ChangeKind {
        TIMELINE,
        QUEUE,
        ENDED
    }

    public void setChangeListener(Consumer<ChangeKind> listener) {
        this.changeListener = listener == null ? change -> {
        } : listener;
    }

    public Optional<PlaybackTimeline> timeline() {
        return Optional.ofNullable(timeline);
    }

    public boolean isPlaying() {
        return timeline != null && timeline.playing();
    }

    public Optional<SharedTrackEntry> currentEntry() {
        return timeline == null ? Optional.empty() : Optional.of(timeline.entry());
    }

    public Optional<QueueSnapshot.QueueEntry> currentQueueEntry() {
        return timeline == null
                ? Optional.empty()
                : Optional.of(new QueueSnapshot.QueueEntry(timeline.entry(), currentRequester));
    }

    public QueueSnapshot queueSnapshot() {
        return new QueueSnapshot(new ArrayList<>(queue), revision);
    }

    public int queueSize() {
        return queue.size();
    }

    public void playNow(QueueSnapshot.QueueEntry entry, long gameTime) {
        pushHistory(timeline);
        timeline = PlaybackTimeline.startedAt(entry.entry(), gameTime);
        currentRequester = entry.requesterName();
        changed(ChangeKind.TIMELINE);
    }

    public void playAt(QueueSnapshot.QueueEntry entry, long gameTime, float positionSeconds) {
        pushHistory(timeline);
        timeline = PlaybackTimeline.resumedAt(entry.entry(), gameTime, positionSeconds);
        currentRequester = entry.requesterName();
        changed(ChangeKind.TIMELINE);
    }

    public boolean pause(long gameTime) {
        if (timeline == null || !timeline.playing()) return false;
        timeline = timeline.withPlaying(gameTime, false);
        changed(ChangeKind.TIMELINE);
        return true;
    }

    public boolean resume(long gameTime) {
        if (timeline == null || timeline.playing()) return false;
        timeline = timeline.withPlaying(gameTime, true);
        changed(ChangeKind.TIMELINE);
        return true;
    }

    public boolean seek(long gameTime, float positionSeconds) {
        if (timeline == null) return false;
        timeline = timeline.seekTo(gameTime, positionSeconds);
        changed(ChangeKind.TIMELINE);
        return true;
    }

    public boolean togglePlayPause(long gameTime) {
        if (timeline == null) return false;
        return timeline.playing() ? pause(gameTime) : resume(gameTime);
    }

    public boolean enqueue(QueueSnapshot.QueueEntry entry) {
        queue.addLast(entry);
        changed(ChangeKind.QUEUE);
        return true;
    }

    public boolean removeQueueIndex(int index) {
        if (index < 0 || index >= queue.size()) return false;
        var iterator = queue.iterator();
        for (var position = 0; position <= index; position++) {
            iterator.next();
        }
        iterator.remove();
        changed(ChangeKind.QUEUE);
        return true;
    }

    public boolean skipToNext(long gameTime) {
        if (timeline == null && queue.isEmpty()) return false;
        pushHistory(timeline);
        advance(gameTime);
        changed(ChangeKind.TIMELINE);
        return true;
    }

    public boolean skipToPrevious(long gameTime) {
        var previous = history.pollLast();
        if (previous == null) {
            if (timeline == null) return false;
            timeline = PlaybackTimeline.startedAt(timeline.entry(), gameTime);
            changed(ChangeKind.TIMELINE);
            return true;
        }
        if (timeline != null) {
            queue.addFirst(new QueueSnapshot.QueueEntry(timeline.entry(), currentRequester));
            revision++;
        }
        timeline = PlaybackTimeline.startedAt(previous.entry(), gameTime);
        currentRequester = previous.requesterName();
        changed(ChangeKind.TIMELINE);
        return true;
    }

    /**
     * 推进已播放完毕的曲目；返回是否发生了推进（调用方据此广播）喵。
     */
    public boolean advanceIfEnded(long gameTime) {
        if (timeline == null || !timeline.playing()) return false;
        if (gameTime < timeline.expectedEndGameTime()) return false;
        advance(gameTime);
        changed(ChangeKind.ENDED);
        return true;
    }

    public void stop(long gameTime) {
        if (timeline == null) return;
        timeline = timeline.withPlaying(gameTime, false);
        changed(ChangeKind.TIMELINE);
    }

    public void clear() {
        queue.clear();
        history.clear();
        timeline = null;
        revision++;
        changed(ChangeKind.QUEUE);
    }

    /**
     * 从持久化快照恢复通道状态（服务端重启用）。历史与重播栈不恢复，重建后从当前曲目继续喵。
     */
    public void restore(
            PlaybackTimeline restoredTimeline,
            String requesterName,
            List<QueueSnapshot.QueueEntry> restoredQueue
    ) {
        queue.clear();
        history.clear();
        if (restoredQueue != null) {
            for (var entry : restoredQueue) {
                if (entry != null) queue.addLast(entry);
            }
        }
        timeline = restoredTimeline;
        currentRequester = requesterName == null ? "" : requesterName;
        revision++;
        changed(ChangeKind.QUEUE);
    }

    private void advance(long gameTime) {
        var next = queue.pollFirst();
        pushHistory(timeline);
        if (next != null) {
            timeline = PlaybackTimeline.startedAt(next.entry(), gameTime);
            currentRequester = next.requesterName();
        } else {
            timeline = null;
            currentRequester = "";
        }
        revision++;
    }

    private void pushHistory(PlaybackTimeline current) {
        if (current == null) return;
        history.addLast(new QueueSnapshot.QueueEntry(current.entry(), ""));
        while (history.size() > HISTORY_LIMIT) {
            history.pollFirst();
        }
    }

    private void changed(ChangeKind kind) {
        changeListener.accept(kind);
    }

    protected List<QueueSnapshot.QueueEntry> historySnapshot() {
        return new ArrayList<>(history);
    }
}
