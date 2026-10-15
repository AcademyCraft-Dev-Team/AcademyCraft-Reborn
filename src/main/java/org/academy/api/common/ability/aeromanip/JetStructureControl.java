package org.academy.api.common.ability.aeromanip;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.LegacyMotionUnits;
import org.academy.api.common.structure.StructurePhysicsControl;
import org.academy.internal.common.ability.program.ProgramPowerScale;

import java.util.List;

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
     * Magnitude of the shared whole-body thrust budget in solver mass units times
     * blocks/second squared. The legacy whole-body acceleration is converted with
     * {@link LegacyMotionUnits} and scaled by the audited milestone factor; the level's
     * absolute force ceiling still applies.
     */
    public static double thrustForceMagnitude(double mass, float power, int milestone) {
        requireMass(mass);
        var legacyThrust = BASE_THRUST_PER_TICK
                * ProgramPowerScale.effectMultiplier(power) * milestoneScale(milestone);
        var force = LegacyMotionUnits.accelerationPerSecondSquared(legacyThrust) * mass;
        return Math.min(force, StructurePhysicsControl.MAX_FORCE);
    }

    /**
     * World-space thrust force for the solver in solver mass units times blocks/second squared.
     */
    public static Vec3 thrustForce(Vec3 thrustDirection, double mass, float power, int milestone) {
        if (!finiteNonZero(thrustDirection)) {
            throw new IllegalArgumentException("Jet thrust direction must be finite and non-zero");
        }
        return thrustDirection.normalize().scale(thrustForceMagnitude(mass, power, milestone));
    }

    /**
     * Combined force of one finite force group. The shared whole-body budget is divided across
     * the supplied thrust directions, so more anchors, nozzles or fragments never multiply it and
     * opposed directions cancel exactly. A direction list that is empty or entirely degenerate
     * produces zero force.
     */
    public static Vec3 combinedThrustForce(
            List<Vec3> thrustDirections,
            double mass,
            float power,
            int milestone
    ) {
        var magnitude = thrustForceMagnitude(mass, power, milestone);
        if (thrustDirections == null || thrustDirections.isEmpty()) return Vec3.ZERO;
        var active = 0;
        for (var direction : thrustDirections) {
            if (finiteNonZero(direction)) active++;
        }
        if (active == 0) return Vec3.ZERO;
        var share = magnitude / active;
        var total = Vec3.ZERO;
        for (var direction : thrustDirections) {
            if (!finiteNonZero(direction)) continue;
            total = total.add(direction.normalize().scale(share));
        }
        return total;
    }

    /**
     * Force for exactly one outer physics step that reproduces the legacy integration
     * {@code velocity = clamp(velocity + thrust, per-tick ceiling)} in converted units for a
     * combined force group. The ceiling applies to the whole vector, exactly like the audited
     * legacy clamp, and the shared budget is never multiplied by the anchor count.
     */
    public static Vec3 limitedCombinedThrustForce(
            List<Vec3> thrustDirections,
            Vec3 velocityPerSecond,
            double mass,
            float power,
            int milestone
    ) {
        requireMass(mass);
        requireFiniteVelocity(velocityPerSecond);
        var force = combinedThrustForce(thrustDirections, mass, power, milestone);
        return limitToCeiling(force, velocityPerSecond, mass, power);
    }

    /** One world-space action point with a world-space thrust direction. */
    public record ThrustAnchor(Vec3 point, Vec3 direction) {
    }

    /**
     * Accounting of one finite force group: the combined force and the {@code Σ(r × F)} torque
     * with {@code r} measured from the center of mass. The shared budget caps the combined force
     * only; the torque is derived from that same group and is never billed as a second budget.
     */
    public record ThrustGroup(Vec3 totalForce, Vec3 totalTorque) {
    }

    /**
     * Shared-budget accounting for a multi-anchor force group. Each anchor contributes an equal
     * share of the one thrust budget along its direction; symmetric anchors therefore cancel in
     * both the combined force and the derived torque.
     */
    public static ThrustGroup thrustGroup(
            List<ThrustAnchor> anchors,
            Vec3 centerOfMass,
            double mass,
            float power,
            int milestone
    ) {
        requireMass(mass);
        if (centerOfMass == null
                || !Double.isFinite(centerOfMass.x)
                || !Double.isFinite(centerOfMass.y)
                || !Double.isFinite(centerOfMass.z)) {
            throw new IllegalArgumentException("Jet center of mass must be finite");
        }
        var directions = anchors == null
                ? List.<Vec3>of()
                : anchors.stream().map(ThrustAnchor::direction).toList();
        var totalForce = combinedThrustForce(directions, mass, power, milestone);
        var totalTorque = Vec3.ZERO;
        if (anchors != null) {
            var active = 0;
            for (var anchor : anchors) {
                if (anchor != null && finiteNonZero(anchor.direction())) active++;
            }
            if (active > 0) {
                var share = thrustForceMagnitude(mass, power, milestone) / active;
                for (var anchor : anchors) {
                    if (anchor == null || !finiteNonZero(anchor.direction())
                            || !finite(anchor.point())) continue;
                    var force = anchor.direction().normalize().scale(share);
                    totalTorque = totalTorque.add(anchor.point().subtract(centerOfMass).cross(force));
                }
            }
        }
        return new ThrustGroup(totalForce, totalTorque);
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
        requireFiniteVelocity(velocityPerSecond);
        var force = thrustForce(thrustDirection, mass, power, milestone);
        return limitToCeiling(force, velocityPerSecond, mass, power);
    }

    private static Vec3 limitToCeiling(
            Vec3 force,
            Vec3 velocityPerSecond,
            double mass,
            float power
    ) {
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

    private static void requireFiniteVelocity(Vec3 velocity) {
        if (!finite(velocity)) {
            throw new IllegalArgumentException("Jet velocity must be finite");
        }
    }

    private static boolean finite(Vec3 value) {
        return value != null
                && Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }

    private static boolean finiteNonZero(Vec3 value) {
        return finite(value) && value.lengthSqr() > 1.0e-8;
    }
}
