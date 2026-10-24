package org.academy.internal.common.structure.upgrade.control;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.upgrade.StructureExtraction;

/** Pure mass/force/acceleration bounded COM servo. Gravity compensation consumes the same budget. */
public final class ExtractionController {
    public static final double POSITION_TOLERANCE = 0.08;
    public static final double SPEED_TOLERANCE = 0.15;

    private ExtractionController() {}

    public record Step(Vec3 velocity, Vec3 displacement, double force, boolean stable) {}

    /**
     * Read-only look-ahead for clearing permitted vegetation before terrain colliders rebuild.
     * This is never a motion command. It covers one outer tick's bounded speed/acceleration,
     * including observed overspeed, but cannot continue beyond the current path waypoint.
     */
    static Vec3 clearanceDisplacement(Vec3 center, Vec3 target, Vec3 observedVelocity,
                                       StructureExtraction.Limits limits) {
        OrientationTargets.requireFinite(center, "center");
        OrientationTargets.requireFinite(target, "target");
        OrientationTargets.requireFinite(observedVelocity, "observedVelocity");
        var offset = target.subtract(center);
        double distance = offset.length();
        if (distance == 0) return Vec3.ZERO;
        double lookAhead = Math.max(limits.maxLinearSpeed(), observedVelocity.length()) * .05
                + limits.maxLinearAcceleration() * .05 * .05;
        return offset.scale(Math.min(distance, lookAhead) / distance);
    }

    public static Step step(Vec3 center, Vec3 velocity, Vec3 gravity, double mass, Vec3 target,
                            StructureExtraction.Limits limits, double seconds) {
        OrientationTargets.requireFinite(center, "center");
        OrientationTargets.requireFinite(velocity, "velocity");
        OrientationTargets.requireFinite(gravity, "gravity");
        OrientationTargets.requireFinite(target, "target");
        if (!Double.isFinite(mass) || mass <= 0 || !Double.isFinite(seconds)
                || seconds <= 0 || seconds > OrientationController.MAX_SUBSTEP_SECONDS) {
            throw new IllegalArgumentException("Invalid linear control observation");
        }
        var error = target.subtract(center);
        double distance = error.length();
        double acceleration = Math.min(limits.maxLinearAcceleration(), limits.maxForce() / mass);
        // Leave gravity headroom when selecting braking speed. Corrections below remain bounded
        // even when a request has too little force to hover; that request must time out honestly.
        double braking = Math.max(0, acceleration - gravity.length());
        double speed = Math.min(limits.maxLinearSpeed(), Math.min(4 * distance, Math.sqrt(2 * braking * distance)));
        var desired = distance == 0 ? Vec3.ZERO : error.scale(speed / distance);
        var delta = desired.subtract(gravity.scale(seconds)).subtract(velocity);
        double maximum = acceleration * seconds;
        if (delta.length() > maximum) {
            if (distance > POSITION_TOLERANCE) {
                // A shove across a narrow extraction corridor must be braked before spending
                // the rest of the force accelerating along it. Uniformly scaling the requested
                // 8 m/s upward change used to retain most of a small lateral shove into a wall.
                var direction = error.scale(1 / distance);
                double along = delta.dot(direction);
                var lateral = delta.subtract(direction.scale(along));
                double lateralLength = lateral.length();
                if (lateralLength >= maximum) delta = lateral.scale(maximum / lateralLength);
                else {
                    double remaining = Math.sqrt(Math.max(0, maximum * maximum - lateralLength * lateralLength));
                    delta = lateral.add(direction.scale(Math.max(-remaining, Math.min(remaining, along))));
                }
            } else delta = delta.scale(maximum / delta.length());
        }
        var next = velocity.add(delta);
        var actual = next.subtract(velocity);
        if (actual.length() > maximum * (1 + 1.0E-12)) { next = velocity; actual = Vec3.ZERO; }
        return new Step(next, next.add(gravity.scale(seconds)).scale(seconds), actual.length() * mass / seconds,
                distance <= POSITION_TOLERANCE && velocity.length() <= SPEED_TOLERANCE);
    }
}
