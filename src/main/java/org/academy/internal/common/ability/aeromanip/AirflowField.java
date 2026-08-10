package org.academy.internal.common.ability.aeromanip;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public record AirflowField(
        UUID id,
        UUID ownerId,
        ResourceKey<Level> dimension,
        Type type,
        Shape shape,
        Vec3 center,
        Vec3 direction,
        double radius,
        double length,
        float strength,
        int durationTicks,
        int proficiencyMilestone
) {
    public AirflowField(
            UUID id,
            UUID ownerId,
            ResourceKey<Level> dimension,
            Type type,
            Shape shape,
            Vec3 center,
            Vec3 direction,
            double radius,
            double length,
            float strength,
            int durationTicks
    ) {
        this(id, ownerId, dimension, type, shape, center, direction, radius, length, strength, durationTicks, 0);
    }

    public AirflowField {
        if (id == null || ownerId == null || dimension == null || type == null || shape == null) {
            throw new IllegalArgumentException("Airflow field identity cannot be null");
        }
        center = finite(center) ? center : Vec3.ZERO;
        direction = finite(direction) && direction.lengthSqr() > 1.0e-8
                ? direction.normalize()
                : new Vec3(0, 1, 0);
        radius = finitePositive(radius);
        length = finitePositive(length);
        strength = Float.isFinite(strength) ? strength : 0.0f;
        durationTicks = Math.max(1, durationTicks);
        proficiencyMilestone = Math.max(0, Math.min(3, proficiencyMilestone));
    }

    static double distanceToSegmentSqr(Vec3 point, Vec3 start, Vec3 end) {
        var segment = end.subtract(start);
        var lengthSqr = segment.lengthSqr();
        if (lengthSqr <= 1.0e-8) return point.distanceToSqr(start);
        var t = Math.max(0.0, Math.min(1.0, point.subtract(start).dot(segment) / lengthSqr));
        return point.distanceToSqr(start.add(segment.scale(t)));
    }

    private static boolean finite(Vec3 value) {
        return value != null
                && Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }

    private static double finitePositive(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }

    private static double square(double value) {
        return value * value;
    }

    public boolean contains(Vec3 point, double padding) {
        if (!finite(point)) return false;
        var expanded = Math.max(0.0, padding);
        return switch (shape) {
            case SPHERE -> point.distanceToSqr(center) <= square(radius + expanded);
            case CAPSULE -> distanceToSegmentSqr(point, center, center.add(direction.scale(length)))
                    <= square(radius + expanded);
            case CONE -> insideCone(point, expanded);
        };
    }

    public AABB bounds() {
        return switch (shape) {
            case SPHERE -> new AABB(center, center).inflate(radius);
            case CAPSULE, CONE -> new AABB(center, center.add(direction.scale(length))).inflate(radius);
        };
    }

    private boolean insideCone(Vec3 point, double padding) {
        var delta = point.subtract(center);
        var forward = delta.dot(direction);
        if (forward < 0.0 || forward > length + padding) return false;
        var allowedRadius = radius * (forward / Math.max(1.0e-6, length)) + padding;
        return delta.subtract(direction.scale(forward)).lengthSqr() <= square(allowedRadius);
    }

    public enum Type {
        TAILWIND,
        VORTEX,
        WIND_CORRIDOR,
        VACUUM,
        ATMOSPHERIC_DOMINION
    }

    public enum Shape {
        SPHERE,
        CAPSULE,
        CONE
    }
}
