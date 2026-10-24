package org.academy.internal.common.sublevel;

import org.academy.api.common.structure.StructurePose;

import java.util.Objects;

/** Client simulation-tick poses shared by SubLevel rendering, picking and contact. */
public final class SubLevelPoseInterpolation {
    private StructurePose previous;
    private StructurePose current;
    private StructurePose target;

    /** Packets only replace the pending target; batched arrivals cannot move a frame mid-tick. */
    public void accept(StructurePose pose) {
        Objects.requireNonNull(pose, "pose");
        if (target == null) {
            previous = pose;
            current = pose;
        }
        target = pose;
    }

    /** Called before entity movement, exactly once per client simulation tick. */
    public StructurePose advance() {
        requirePose();
        previous = current;
        current = target;
        return current;
    }

    public StructurePose current() {
        requirePose();
        return current;
    }

    /** Mirrors vanilla entity previous/current interpolation; independent of world-time updates. */
    public StructurePose sample(double partialTick) {
        requirePose();
        if (!Double.isFinite(partialTick)) throw new IllegalArgumentException("Partial tick must be finite");
        double fraction = Math.clamp(partialTick, 0.0, 1.0);
        if (fraction == 0.0) return previous;
        if (fraction == 1.0 || previous.equals(current)) return current;
        return previous.lerp(current, fraction);
    }

    private void requirePose() {
        if (target == null) throw new IllegalStateException("No SubLevel pose has been received");
    }
}
