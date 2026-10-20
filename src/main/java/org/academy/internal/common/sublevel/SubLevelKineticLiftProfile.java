package org.academy.internal.common.sublevel;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.ability.accelerator.KineticLiftProfile;
import org.academy.api.common.structure.StructureMotionState;

/**
 * Shared kinetic-lift calibration. Selection tiers 0/1/2 rise by 3/5/7 blocks, measured at the
 * actual world center of mass rather than a grid origin or a ray-hit anchor.
 */
public final class SubLevelKineticLiftProfile implements KineticLiftProfile {
    public static final double[] HEIGHTS = {3.0, 5.0, 7.0};
    public static final double ACCEPTANCE_TOLERANCE = 0.1;
    public static final int HOVER_TICKS = 200;

    @Override
    public LiftTarget planTarget(int selectionTier, SourceBaseline source) {
        if (selectionTier < 0 || selectionTier > 2) {
            throw new IllegalArgumentException("Selection tier must be in 0..2");
        }
        if (source == null || source.sourceGridPose() == null || source.sourceLocalCenterOfMass() == null) {
            throw new IllegalArgumentException("A lift target requires a source pose and local center of mass");
        }
        var localCenter = source.sourceLocalCenterOfMass();
        if (!finite(localCenter)) throw new IllegalArgumentException("Source center of mass must be finite");
        var sourceWorld = source.sourceGridPose().localToWorldPoint(localCenter);
        double height = HEIGHTS[selectionTier];
        var targetWorld = sourceWorld.add(0.0, height, 0.0);
        if (!finite(sourceWorld) || !finite(targetWorld)) {
            throw new IllegalArgumentException("Lift target is not finite");
        }
        return new LiftTarget(selectionTier, height, sourceWorld, targetWorld);
    }

    @Override
    public LiftMeasurement measureCompletion(LiftTarget target, StructureMotionState observation,
                                             double heightToleranceBlocks) {
        if (target == null || observation == null) {
            throw new IllegalArgumentException("Lift measurement requires a target and an observation");
        }
        if (!Double.isFinite(heightToleranceBlocks) || heightToleranceBlocks < 0) {
            throw new IllegalArgumentException("Height tolerance must be finite and nonnegative");
        }
        var actual = observation.pose().localToWorldPoint(observation.localCenterOfMass());
        if (!finite(actual)) throw new IllegalArgumentException("Measured center of mass is not finite");
        double rise = actual.y - target.sourceWorldCenterOfMass().y;
        double error = target.targetWorldCenterOfMass().y - actual.y;
        boolean reached = rise >= target.requestedHeightBlocks() - heightToleranceBlocks;
        return new LiftMeasurement(actual, rise, error, reached);
    }

    @Override
    public long hoverDeadline(long firstReachedServerTick) {
        if (firstReachedServerTick < 0) throw new IllegalArgumentException("Completion tick cannot be negative");
        return Math.addExact(firstReachedServerTick, HOVER_TICKS);
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}
