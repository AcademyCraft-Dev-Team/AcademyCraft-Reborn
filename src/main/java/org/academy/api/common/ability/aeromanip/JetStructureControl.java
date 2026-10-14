package org.academy.api.common.ability.aeromanip;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.LegacyMotionUnits;
import org.academy.api.common.structure.StructurePhysicsControl;
import org.academy.internal.common.ability.program.ProgramPowerScale;

/**
 * Shared high-speed jet propulsion calibration for skills, precision programs and non-player
 * ability actors. Every value converts the audited legacy per-tick motion: the legacy thruster
 * adds {@link #BASE_THRUST_PER_TICK} blocks/tick² to the whole body (mass-normalized), and the
 * legacy speed ceiling is 3/6/8 blocks/tick at controlled/standard/maximum program power.
 * One outer native physics step is fixed at {@link #PHYSICS_STEP_SECONDS}.
 */
public final class JetStructureControl {
    /** Legacy whole-body thrust in blocks per tick squared, before mass normalization. */
    public static final double BASE_THRUST_PER_TICK = 0.48;
    /** Legacy milestone-3 whole-body thrust scale. */
    public static final double MILESTONE_THRUST_SCALE = 1.25;
    /** Legacy controlled power speed ceiling in blocks per tick. */
    public static final double CONTROLLED_SPEED_PER_TICK = 3.0;
    /** Legacy standard power speed ceiling in blocks per tick. */
    public static final double STANDARD_SPEED_PER_TICK = 6.0;
    /** Legacy maximum power speed ceiling in blocks per tick. */
    public static final double MAXIMUM_SPEED_PER_TICK = 8.0;
    /** Fixed duration of one outer native physics step in seconds. */
    public static final double PHYSICS_STEP_SECONDS = 0.05;

    private JetStructureControl() {}

    /** Legacy milestone 3 raises each nozzle's thrust by 25 percent. */
    public static double milestoneScale(int milestone) {
        return milestone >= 3 ? MILESTONE_THRUST_SCALE : 1.0;
    }

    /** Legacy per-tick speed ceiling converted to the common solver's blocks per second. */
    public static double maximumSpeed(float power) {
        return LegacyMotionUnits.velocityPerSecond(ProgramPowerScale.interpolate(
                power, CONTROLLED_SPEED_PER_TICK, STANDARD_SPEED_PER_TICK, MAXIMUM_SPEED_PER_TICK));
    }

    /**
     * World-space thrust force for the solver in solver mass units times blocks/second squared.
     * The legacy whole-body acceleration is converted with {@link LegacyMotionUnits} and scaled
     * by the audited milestone factor; the level's absolute force ceiling still applies.
     */
    public static Vec3 thrustForce(Vec3 thrustDirection, double mass, float power, int milestone) {
        requireMass(mass);
        if (thrustDirection == null || thrustDirection.lengthSqr() <= 1.0e-8) {
            throw new IllegalArgumentException("Jet thrust direction must be finite and non-zero");
        }
        var legacyThrust = BASE_THRUST_PER_TICK
                * ProgramPowerScale.effectMultiplier(power) * milestoneScale(milestone);
        var force = thrustDirection.normalize().scale(
                LegacyMotionUnits.accelerationPerSecondSquared(legacyThrust) * mass);
        var length = force.length();
        return length > StructurePhysicsControl.MAX_FORCE
                ? force.scale(StructurePhysicsControl.MAX_FORCE / length)
                : force;
    }

    /**
     * Force for exactly one outer physics step that reproduces the legacy integration
     * {@code velocity = clamp(velocity + thrust, per-tick ceiling)} in converted units.
     * The velocity ceiling is applied to the whole vector, exactly like the audited legacy clamp.
     */
    public static Vec3 limitedThrustForce(
            Vec3 thrustDirection,
            Vec3 velocityPerSecond,
            double mass,
            float power,
            int milestone
    ) {
        requireMass(mass);
        if (velocityPerSecond == null
                || !Double.isFinite(velocityPerSecond.x)
                || !Double.isFinite(velocityPerSecond.y)
                || !Double.isFinite(velocityPerSecond.z)) {
            throw new IllegalArgumentException("Jet velocity must be finite");
        }
        var force = thrustForce(thrustDirection, mass, power, milestone);
        var next = velocityPerSecond.add(force.scale(PHYSICS_STEP_SECONDS / mass));
        var maximum = maximumSpeed(power);
        if (next.lengthSqr() > maximum * maximum) {
            next = next.scale(maximum / next.length());
        }
        return next.subtract(velocityPerSecond).scale(mass / PHYSICS_STEP_SECONDS);
    }

    private static void requireMass(double value) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException("Body mass must be finite and positive");
        }
    }
}
