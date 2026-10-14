package org.academy.api.common.ability.electromaster;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.LegacyMotionUnits;
import org.academy.api.common.structure.StructurePhysicsControl;

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
     * Force for exactly one outer physics step that reproduces the legacy per-tick pull
     * velocity in converted units. The legacy profile runs in blocks per tick, the solver
     * uses blocks per second, and the full force vector is clamped to the shared absolute
     * ceiling so an excessive request is bounded here instead of being rejected downstream.
     */
    public static Vec3 pullForce(Vec3 currentVelocityPerSecond, Vec3 origin, Vec3 target,
                                 Vec3 fallbackDirection, double maxSpeedPerTick, double stopDistance,
                                 double mass) {
        requireMass(mass);
        var desiredPerTick = MagneticMovement.calculatePullVelocity(
                LegacyMotionUnits.velocityPerTick(currentVelocityPerSecond),
                origin, target, fallbackDirection, maxSpeedPerTick, stopDistance);
        var force = LegacyMotionUnits.velocityPerSecond(desiredPerTick)
                .subtract(currentVelocityPerSecond)
                .scale(mass / PHYSICS_STEP_SECONDS);
        var limit = StructurePhysicsControl.MAX_FORCE - 1.0e-6;
        var length = force.length();
        return length > limit ? force.scale(limit / length) : force;
    }

    private static void requireMass(double value) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException("Body mass must be finite and positive");
        }
    }
}
