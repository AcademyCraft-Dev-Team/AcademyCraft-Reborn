package org.academy.api.common.ability.accelerator;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructureMotionState;
import org.academy.api.common.structure.StructurePose;

/**
 * Contract for kinetic lift targets and observations, shared by skills and other ability actors.
 * This API is a scaffold for Agent B; it is not yet connected to existing skill sessions.
 */
public interface KineticLiftProfile {
    /**
     * Plans selection tiers 0/1/2 as a rise of 3/5/7 blocks respectively. The target retains
     * the source world center of mass's X/Z coordinates. The source world center of mass
     * must be transformed from the supplied source pose and local center of mass; neither
     * the grid origin nor a ray-hit anchor is an interchangeable measurement point.
     *
     * @param selectionTier the captured selection's tier, in the inclusive range 0..2
     * @param source the capture baseline, recorded before the first physics step
     */
    LiftTarget planTarget(int selectionTier, SourceBaseline source);

    /**
     * Measures the actual world center of mass by transforming the observation's local
     * center of mass with its current pose. Rise is measured from the target's source world
     * center of mass. Reached means actual rise is at least the requested rise minus the
     * explicit finite, nonnegative tolerance; a commanded pose alone cannot establish it.
     * There is no implicit 0.6-block early-completion allowance.
     *
     * @param heightToleranceBlocks the explicit height acceptance tolerance in blocks
     */
    LiftMeasurement measureCompletion(LiftTarget target, StructureMotionState observation,
                                      double heightToleranceBlocks);

    /**
     * Returns the first actual completion tick plus 200 server ticks (ten seconds at 20 TPS).
     * The caller latches that first tick once, retains the lift target during the interval,
     * and must not extend the deadline on subsequent successful measurements.
     * Timing does not itself authorize a throw or change manual/program session ownership.
     */
    long hoverDeadline(long firstReachedServerTick);

    /**
     * Source grid pose and its matching structure-local center of mass. The pose origin is
     * local (0, 0, 0) in world space, not the center of mass. For a newly captured source grid,
     * preserve the source pose instead of sampling an already drifting body's current pose.
     */
    record SourceBaseline(StructurePose sourceGridPose, Vec3 sourceLocalCenterOfMass) {
    }

    /** Both positions are world-space centers of mass, never grid origins or hit anchors. */
    record LiftTarget(int selectionTier, double requestedHeightBlocks,
                      Vec3 sourceWorldCenterOfMass, Vec3 targetWorldCenterOfMass) {
    }

    /** Height error is target Y minus actual Y; positive values mean the body is below target. */
    record LiftMeasurement(Vec3 actualWorldCenterOfMass, double actualRiseBlocks,
                           double heightErrorBlocks, boolean reached) {
    }
}
