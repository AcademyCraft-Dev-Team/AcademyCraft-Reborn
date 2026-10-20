package org.academy.internal.common.sublevel.pipeline;

import org.academy.api.common.structure.StructurePipeline;
import org.academy.api.common.structure.StructurePose;
import org.joml.Quaterniond;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

/**
 * PIPE-06 draft pose timeline. Samples an ordered, bounded history in server ticks: interpolation
 * inside the window, a finite velocity extrapolation past the newest frame, and a frozen stale
 * sample when that window is exceeded. Teleport frames discard older history.
 */
public final class PipelinePoseTimeline {
    public static final int MAX_HISTORY_FRAMES = 256;
    public static final double MAX_EXTRAPOLATION_LIMIT = 20.0;
    private static final double EPSILON = 1.0E-9;

    public StructurePipeline.PoseSample sample(List<StructurePipeline.PoseFrame> history,
                                               double serverTick, double maximumExtrapolationTicks) {
        if (history == null || history.isEmpty()) throw new IllegalArgumentException("Pose history is required");
        if (!Double.isFinite(serverTick) || serverTick < 0) {
            throw new IllegalArgumentException("Server tick must be finite and nonnegative");
        }
        if (!Double.isFinite(maximumExtrapolationTicks) || maximumExtrapolationTicks < 0
                || maximumExtrapolationTicks > MAX_EXTRAPOLATION_LIMIT) {
            throw new IllegalArgumentException("Extrapolation must be finite and in [0, " + MAX_EXTRAPOLATION_LIMIT + "]");
        }
        StructurePipeline.Epoch epoch = null;
        long newestTick = Long.MIN_VALUE;
        long newestSequence = Long.MIN_VALUE;
        for (var frame : history) {
            validate(frame);
            // A new epoch restarts its sequence, so the latest server tick selects the epoch;
            // sequence only breaks ties at the same tick.
            if (frame.serverTick() > newestTick
                    || (frame.serverTick() == newestTick && frame.sequence() > newestSequence)) {
                newestTick = frame.serverTick();
                newestSequence = frame.sequence();
                epoch = frame.epoch();
            }
        }
        if (epoch == null) throw new IllegalArgumentException("Pose history has no epoch");
        var bySequence = new TreeMap<Long, StructurePipeline.PoseFrame>();
        for (var frame : history) {
            if (!frame.epoch().equals(epoch)) continue;
            bySequence.put(frame.sequence(), frame);
        }
        long lastTick = Long.MIN_VALUE;
        for (var frame : bySequence.values()) {
            if (frame.serverTick() < lastTick) {
                throw new IllegalArgumentException("Pose history tick order disagrees with its sequence");
            }
            lastTick = frame.serverTick();
        }
        var frames = new ArrayList<>(bySequence.values());
        frames.sort(Comparator.comparingLong(StructurePipeline.PoseFrame::serverTick)
                .thenComparingLong(StructurePipeline.PoseFrame::sequence));
        if (frames.size() > MAX_HISTORY_FRAMES) {
            frames = new ArrayList<>(frames.subList(frames.size() - MAX_HISTORY_FRAMES, frames.size()));
        }
        // A teleport frame discards older history; a sample before it is stale at that frame.
        int start = 0;
        for (int index = 0; index < frames.size(); index++) {
            if (frames.get(index).teleport() && frames.get(index).serverTick() <= serverTick + EPSILON) {
                start = index;
            }
        }
        frames = new ArrayList<>(frames.subList(start, frames.size()));
        var first = frames.getFirst();
        if (serverTick < first.serverTick() - EPSILON) {
            return new StructurePipeline.PoseSample(epoch, first.pose(), first.sequence(), false, true);
        }
        var last = frames.getLast();
        if (serverTick > last.serverTick() + EPSILON) {
            double delta = serverTick - last.serverTick();
            if (delta > maximumExtrapolationTicks + EPSILON) {
                return new StructurePipeline.PoseSample(epoch, last.pose(), last.sequence(), false, true);
            }
            return new StructurePipeline.PoseSample(epoch, extrapolate(last, delta), last.sequence(), true, false);
        }
        for (int index = 0; index < frames.size(); index++) {
            var frame = frames.get(index);
            if (Math.abs(frame.serverTick() - serverTick) <= EPSILON) {
                return new StructurePipeline.PoseSample(epoch, frame.pose(), frame.sequence(), false, false);
            }
            if (frame.serverTick() < serverTick && index + 1 < frames.size()) {
                var next = frames.get(index + 1);
                if (next.serverTick() > serverTick) {
                    double span = next.serverTick() - frame.serverTick();
                    double fraction = span <= 0 ? 1.0 : (serverTick - frame.serverTick()) / span;
                    return new StructurePipeline.PoseSample(epoch, frame.pose().lerp(next.pose(), fraction),
                            next.sequence(), false, false);
                }
            }
        }
        return new StructurePipeline.PoseSample(epoch, last.pose(), last.sequence(), false, true);
    }

    private static StructurePose extrapolate(StructurePipeline.PoseFrame frame, double delta) {
        var origin = frame.pose().worldOrigin().add(frame.linearVelocity().scale(delta));
        var omega = frame.angularVelocity();
        double angle = omega.length() * delta;
        if (!(angle > EPSILON)) return new StructurePose(origin, frame.pose().rotation());
        var axis = omega.normalize();
        var rotation = new Quaterniond().fromAxisAngleRad(axis.x, axis.y, axis.z, angle)
                .mul(frame.pose().rotation());
        return new StructurePose(origin, rotation);
    }

    private static void validate(StructurePipeline.PoseFrame frame) {
        if (frame == null || frame.epoch() == null || frame.epoch().reference() == null
                || frame.epoch().runtimeId() == null || frame.epoch().revision() < 0
                || frame.pose() == null || frame.linearVelocity() == null || frame.angularVelocity() == null) {
            throw new IllegalArgumentException("Pose frame is incomplete");
        }
        if (frame.sequence() < 0 || frame.serverTick() < 0) {
            throw new IllegalArgumentException("Pose frame identity must be nonnegative");
        }
        if (!finite(frame.linearVelocity()) || !finite(frame.angularVelocity())) {
            throw new IllegalArgumentException("Pose frame velocity must be finite");
        }
    }

    private static boolean finite(net.minecraft.world.phys.Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}
