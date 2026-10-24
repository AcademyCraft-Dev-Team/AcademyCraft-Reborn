package org.academy.api.common.ability.electromaster;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.LegacyMotionUnits;
import org.academy.api.common.structure.StructurePhysicsControl;
import org.academy.api.common.structure.StructurePhysicsControls;
import org.academy.api.common.structure.StructureMotionState;
import org.academy.api.common.structure.StructureDynamics;

/**
 * Shared magnetic-pull budgets for skills, precision programs and non-player ability actors.
 * The native pull reuses the audited legacy per-tick velocity profile
 * ({@link MagneticMovement#calculatePullVelocity}) and converts one outer physics step of
 * velocity error into a bounded force; the shared absolute force ceiling still applies.
 */
public final class MagneticStructureControl {
    /** Fixed duration of one outer native physics step in seconds. */
    public static final double PHYSICS_STEP_SECONDS = 0.05;
    /** Exclusive control lease used by every magnetic pull session. */
    public static final int TRACTION_LEASE_TICKS = 20;
    /** Total mass admitted at milestone 0; each higher proficiency stage doubles the budget. */
    public static final int BASE_MASS_BUDGET = 128;

    private MagneticStructureControl() {}

    /** Proficiency milestones 0/1/2+ admit 128/256/512 total mass. */
    public static int massBudget(int milestone) {
        return BASE_MASS_BUDGET << Math.clamp(milestone, 0, 2);
    }

    /**
     * Bounded magnetic participation of one captured structure: natively magnetic cells over all
     * captured cells, clamped to [0, 1]. An empty selection reports full participation because no
     * mass estimate exists; a structure with one magnetic cell and many inert cells reports the
     * same ratio, so a single magnetic cell cannot accelerate the whole body.
     */
    public static double magneticParticipation(int magneticCells, int totalCells) {
        if (totalCells <= 0) return 1.0;
        return Math.clamp((double) Math.max(0, magneticCells) / totalCells, 0.0, 1.0);
    }

    /**
     * Force for exactly one outer physics step that reproduces the legacy per-tick pull
     * velocity in converted units. The legacy profile runs in blocks per tick, the solver
     * uses blocks per second, and the full force vector is clamped to the shared absolute
     * ceiling so an excessive request is bounded here instead of being rejected downstream.
     */
    public static Vec3 pullForce(Vec3 currentVelocityPerSecond, Vec3 origin, Vec3 target,
                                 Vec3 fallbackDirection, double maxSpeedPerTick, double stopDistance,
                                 double mass) {
        return pullForce(currentVelocityPerSecond, origin, target, fallbackDirection,
                maxSpeedPerTick, stopDistance, mass, 1.0);
    }

    /**
     * Same converted pull profile scaled by the structure's magnetic participation. The whole
     * body's total mass still sets the acceleration through {@code mass}, but only the magnetic
     * share of it can be driven; a participation of zero produces no force at all.
     */
    public static Vec3 pullForce(Vec3 currentVelocityPerSecond, Vec3 origin, Vec3 target,
                                 Vec3 fallbackDirection, double maxSpeedPerTick, double stopDistance,
                                 double mass, double participation) {
        requireMass(mass);
        var desiredPerTick = MagneticMovement.calculatePullVelocity(
                LegacyMotionUnits.velocityPerTick(currentVelocityPerSecond),
                origin, target, fallbackDirection, maxSpeedPerTick, stopDistance);
        var force = LegacyMotionUnits.velocityPerSecond(desiredPerTick)
                .subtract(currentVelocityPerSecond)
                .scale(mass / PHYSICS_STEP_SECONDS);
        var limit = StructurePhysicsControl.MAX_FORCE - 1.0e-6;
        var length = force.length();
        if (length > limit) force = force.scale(limit / length);
        var bounded = Math.clamp(participation, 0.0, 1.0);
        return bounded >= 1.0 ? force : force.scale(bounded);
    }

    /**
     * Pulls a structure-local action point. Its velocity includes angular motion about the
     * center of mass, so an off-center magnet cannot amplify its own rotation by mistaking
     * a stationary center of mass for a stationary action point.
     */
    public static Vec3 pullForce(StructureMotionState motion, Vec3 localPoint, Vec3 target,
                                 Vec3 fallbackDirection, double maxSpeedPerTick, double stopDistance,
                                 double participation) {
        return pullForce(StructurePhysicsControls.pointVelocity(localPoint, motion),
                motion.pose().localToWorldPoint(localPoint), target, fallbackDirection,
                maxSpeedPerTick, stopDistance, motion.mass(), participation);
    }

    /** Same local-point feedback for backend-neutral motion observations. */
    public static Vec3 pullForce(StructureDynamics.State motion, Vec3 localPoint, Vec3 target,
                                 Vec3 fallbackDirection, double maxSpeedPerTick, double stopDistance,
                                 double participation) {
        var velocity = motion.pose().surfaceVelocity(localPoint, motion.localCenterOfMass(),
                motion.linearVelocity(), motion.angularVelocity());
        return pullForce(velocity, motion.pose().localToWorldPoint(localPoint), target, fallbackDirection,
                maxSpeedPerTick, stopDistance, motion.mass(), participation);
    }

    private static void requireMass(double value) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException("Body mass must be finite and positive");
        }
    }
}
