package org.academy.internal.common.structure.space;

import org.academy.api.common.structure.StructurePose;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Optional;

/** Bounded server-published observations, scoped by the owning handle's runtime lifetime. */
public final class NativeStructurePoseHistory {
    public static final long MAX_AGE_TICKS = 6;
    private static final int MAX_FRAMES = 8;
    private final ArrayDeque<Frame> frames = new ArrayDeque<>();

    public record Frame(long tick, long revision, long contentRevision, StructurePose pose) {}

    public void remember(long tick, long revision, long contentRevision, StructurePose pose) {
        if (tick < 0 || revision < 0 || contentRevision < 0) throw new IllegalArgumentException("Negative observation version");
        Objects.requireNonNull(pose, "pose");
        var last = frames.peekLast();
        if (last != null) {
            // Clock/version regression is a new continuity domain, never a wraparound match.
            if (tick < last.tick || revision < last.revision || contentRevision != last.contentRevision) clear();
            else if (revision == last.revision) {
                if (!pose.equals(last.pose)) throw new IllegalArgumentException("One version cannot publish two poses");
                frames.removeLast();
            }
        }
        frames.addLast(new Frame(tick, revision, contentRevision, pose));
        while (frames.size() > MAX_FRAMES) frames.removeFirst();
        expire(tick);
    }

    public Optional<Frame> find(long now, long revision, long contentRevision) {
        expire(now);
        return frames.stream().filter(frame -> frame.revision == revision && frame.contentRevision == contentRevision).findFirst();
    }

    public void clear() { frames.clear(); }

    private void expire(long now) {
        if (now < 0 || !frames.isEmpty() && now < frames.getLast().tick) { clear(); return; }
        while (!frames.isEmpty() && now - frames.getFirst().tick > MAX_AGE_TICKS) frames.removeFirst();
    }
}
