package org.academy.api.common.ability.electromaster;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Entity-independent motion profiles for magnetic anchoring and terrain-following flight. */
public final class MagneticMovement {
    public static final double DEFAULT_HOVER_HEIGHT = 1.5;
    public static final double MIN_HOVER_HEIGHT = 0.5;
    public static final double MAX_HOVER_HEIGHT = 4.0;
    public static final double GROUND_REACH = 8.0;
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

    public static double adjustHoverHeight(double current, boolean up, boolean down) {
        return Math.clamp(current + ((up ? 1 : 0) - (down ? 1 : 0)) * 0.12,
                MIN_HOVER_HEIGHT, MAX_HOVER_HEIGHT);
    }

    public static Vec3 flightDirection(float yaw, double forward, double strafe, double speed) {
        if (!Float.isFinite(yaw) || !Double.isFinite(forward) || !Double.isFinite(strafe)
                || !Double.isFinite(speed) || speed <= 0.0) return Vec3.ZERO;
        var radians = Math.toRadians(yaw);
        var direction = new Vec3(strafe * Math.cos(radians) - forward * Math.sin(radians),
                0.0, forward * Math.cos(radians) + strafe * Math.sin(radians));
        return direction.lengthSqr() > 1.0 ? direction.normalize().scale(speed) : direction.scale(speed);
    }

    public static Vec3 approachVelocity(Vec3 current, Vec3 feet, Vec3 destination, double maxSpeed) {
        if (!finite(current) || !finite(feet) || !finite(destination)
                || !Double.isFinite(maxSpeed) || maxSpeed <= 0.0) return Vec3.ZERO;
        var offset = destination.subtract(feet);
        var desired = offset.scale(0.4);
        if (desired.length() > maxSpeed) desired = desired.normalize().scale(maxSpeed);
        return approach(current, desired);
    }

    public static Vec3 hoverVelocity(Vec3 current, Vec3 horizontal, double feetY,
                                     double groundY, double hoverHeight) {
        if (!finite(current) || !finite(horizontal) || !Double.isFinite(feetY)
                || !Double.isFinite(groundY) || !Double.isFinite(hoverHeight)) return Vec3.ZERO;
        var vertical = Math.clamp((groundY + hoverHeight - feetY) * 0.4, -0.45, 0.45);
        return approach(current, new Vec3(horizontal.x, vertical, horizontal.z));
    }

    public static Vec3 approach(Vec3 current, Vec3 desired) {
        var change = desired.subtract(current);
        return change.length() <= ACCELERATION ? desired
                : current.add(change.normalize().scale(ACCELERATION));
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
