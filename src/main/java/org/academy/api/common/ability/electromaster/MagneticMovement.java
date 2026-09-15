package org.academy.api.common.ability.electromaster;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Entity-independent motion profiles for magnetic anchoring and terrain-following flight. */
public final class MagneticMovement {
    /** Hard floor for the feet-to-ground gap, so terrain can never push into the mover. */
    public static final double MIN_CLEARANCE = 0.3;
    public static final double ACCELERATION = 0.08;

    private MagneticMovement() {
    }

    /** Returns a feet position outside the hit face, accounting for the subject's dimensions. */
    public static Vec3 anchorPosition(Vec3 contact, Direction face, double width, double height,
                                      double hoverHeight) {
        return switch (face) {
            case UP -> contact.add(0.0, hoverHeight, 0.0);
            case DOWN -> contact.add(0.0, -height - 0.15, 0.0);
            default -> contact.add(face.getStepX() * (width * 0.5 + 0.15),
                    -height * 0.5, face.getStepZ() * (width * 0.5 + 0.15));
        };
    }

    public static Vec3 flightDirection(float yaw, double forward, double strafe, double speed) {
        if (!Float.isFinite(yaw) || !Double.isFinite(forward) || !Double.isFinite(strafe)
                || !Double.isFinite(speed) || speed <= 0.0) return Vec3.ZERO;
        var radians = Math.toRadians(yaw);
        var direction = new Vec3(strafe * Math.cos(radians) - forward * Math.sin(radians),
                0.0, forward * Math.cos(radians) + strafe * Math.sin(radians));
        return direction.lengthSqr() > 1.0 ? direction.normalize().scale(speed) : direction.scale(speed);
    }

    /**
     * Vertical speed that holds or restores a feet-to-ground clearance.
     *
     * <p>Vertical input moves the target clearance away from {@code tuning.restClearance()} rather than
     * setting a speed directly, so altitude is always defined relative to whatever surface is underfoot.
     * Rising is clamped more generously than falling: ground coming up into the mover is what must not be
     * clipped through, while an unsupported drop is allowed to be slow.</p>
     */
    public static double hoverVertical(double input, double clearance, LevitationTuning tuning) {
        return hoverVertical(input, clearance, Double.POSITIVE_INFINITY, tuning);
    }

    /**
     * Variant whose target clearance cannot exceed {@code maxClearance}.
     *
     * <p>Levitation is bounded by how far the field reaches, so an unbounded target would leave the mover
     * asking for altitude the field can never provide — climbing into a clamp forever instead of settling
     * at a steady ceiling. Pass the field reach so the model only requests what is reachable.</p>
     */
    public static double hoverVertical(double input, double clearance, double maxClearance,
                                       LevitationTuning tuning) {
        if (!Double.isFinite(input) || !Double.isFinite(clearance) || tuning == null) return 0.0;
        var ceiling = Double.isFinite(maxClearance) && maxClearance >= MIN_CLEARANCE
                ? maxClearance : Double.POSITIVE_INFINITY;
        var target = Math.min(ceiling, Math.max(MIN_CLEARANCE,
                tuning.restClearance() + Math.clamp(input, -1.0, 1.0) * tuning.inputClearanceOffset()));
        var error = target - clearance;
        return Math.clamp(error * tuning.followGain(), -tuning.maxDescentSpeed(), tuning.maxClimbSpeed());
    }

    /**
     * Vertical speed while a lost field is still recoverable. Vertical input keeps full climb authority so
     * a mover can lift itself back into range, while no input sinks into the grace window and downward
     * input is capped at the ordinary descent speed.
     */
    public static double degradedVertical(double input, LevitationTuning tuning) {
        if (tuning == null) return 0.0;
        var intent = Double.isFinite(input) ? Math.clamp(input, -1.0, 1.0) : 0.0;
        if (intent > 0.0) return intent * tuning.maxClimbSpeed();
        return Math.clamp(intent * tuning.inputVerticalSpeed() - tuning.sinkSpeed(),
                -tuning.maxDescentSpeed(), 0.0);
    }

    public static Vec3 approachVelocity(Vec3 current, Vec3 feet, Vec3 destination, double maxSpeed) {
        if (!finite(current) || !finite(feet) || !finite(destination)
                || !Double.isFinite(maxSpeed) || maxSpeed <= 0.0) return Vec3.ZERO;
        var offset = destination.subtract(feet);
        var desired = offset.scale(0.4);
        if (desired.length() > maxSpeed) desired = desired.normalize().scale(maxSpeed);
        return approach(current, desired);
    }

    public static Vec3 approach(Vec3 current, Vec3 desired) {
        if (!finite(current) || !finite(desired)) return Vec3.ZERO;
        var change = desired.subtract(current);
        return change.length() <= ACCELERATION ? desired
                : current.add(change.normalize().scale(ACCELERATION));
    }

    /**
     * Smooths only the horizontal components. Vertical motion comes from the clearance solver, which is
     * already rate-limited, so it must not be slowed down again by horizontal acceleration limits.
     */
    public static Vec3 approachHorizontal(Vec3 current, Vec3 desired) {
        if (!finite(current) || !finite(desired)) return Vec3.ZERO;
        var smoothed = approach(new Vec3(current.x, 0.0, current.z), new Vec3(desired.x, 0.0, desired.z));
        return new Vec3(smoothed.x, 0.0, smoothed.z);
    }

    public static Vec3 calculatePullVelocity(Vec3 currentVelocity, Vec3 origin, Vec3 target,
                                      Vec3 fallbackDirection, double maxSpeed, double stopDistance) {
        if (!finite(currentVelocity) || !finite(origin) || !finite(target) || !finite(fallbackDirection)) {
            return Vec3.ZERO;
        }
        var direction = target.subtract(origin);
        var distance = direction.length();
        if (!Double.isFinite(distance)) return Vec3.ZERO;
        if (distance <= stopDistance) return currentVelocity.scale(0.25);
        if (distance <= 1.0e-6) direction = fallbackDirection;
        if (direction.lengthSqr() <= 1.0e-6) return Vec3.ZERO;
        var speed = Math.clamp((distance - stopDistance) * 0.22, 0.12, maxSpeed);
        var desired = direction.normalize().scale(speed);
        var velocity = currentVelocity.scale(0.2).add(desired.scale(0.8));
        var length = velocity.length();
        return length > maxSpeed ? velocity.scale(maxSpeed / length) : velocity;
    }


    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x)
                && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}
