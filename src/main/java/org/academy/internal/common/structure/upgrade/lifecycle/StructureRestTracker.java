package org.academy.internal.common.structure.upgrade.lifecycle;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.academy.internal.common.structure.upgrade.control.OrientationTargets;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** Pure rest qualification. Paused terrain, controls, changed content and missing samples reset it. */
public final class StructureRestTracker {
    public static final double MAX_LINEAR_SPEED = .05;
    public static final double MAX_ANGULAR_SPEED = .05;
    private static final double MAX_DRIFT = .025;
    private static final double MAX_ANGLE_DRIFT = .025;
    private @Nullable Observation baseline;
    private long since;
    private long previousTick;

    public record Observation(UUID runtimeId, long contentRevision, StructurePose pose,
                              Vec3 linearVelocity, Vec3 angularVelocity, boolean terrainReady,
                              boolean controlled) {}

    /** Samples may be budget-delayed; a long observation gap cannot count as known rest. */
    public boolean observe(Observation observation, long tick, int requiredTicks, int maximumGap) {
        if (!eligible(observation)) { reset(); return false; }
        if (baseline == null || tick < previousTick || tick - previousTick > maximumGap
                || !baseline.runtimeId().equals(observation.runtimeId())
                || baseline.contentRevision() != observation.contentRevision()
                || baseline.pose().worldOrigin().distanceToSqr(observation.pose().worldOrigin()) > MAX_DRIFT * MAX_DRIFT
                || OrientationTargets.rotationError(baseline.pose(), observation.pose()).length() > MAX_ANGLE_DRIFT) {
            baseline = observation;
            since = tick;
        }
        previousTick = tick;
        return tick - since >= requiredTicks;
    }

    public void reset() { baseline = null; }

    public static boolean eligible(Observation observation) {
        return observation != null && observation.terrainReady() && !observation.controlled()
                && finite(observation.linearVelocity()) && finite(observation.angularVelocity())
                && observation.linearVelocity().lengthSqr() < MAX_LINEAR_SPEED * MAX_LINEAR_SPEED
                && observation.angularVelocity().lengthSqr() < MAX_ANGULAR_SPEED * MAX_ANGULAR_SPEED;
    }

    private static boolean finite(Vec3 velocity) {
        return velocity != null && Double.isFinite(velocity.x) && Double.isFinite(velocity.y)
                && Double.isFinite(velocity.z);
    }
}
