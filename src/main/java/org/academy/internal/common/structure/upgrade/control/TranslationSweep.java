package org.academy.internal.common.structure.upgrade.control;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;

import java.util.Objects;
import java.util.Optional;

/** Continuous, fixed-orientation translation of two real cuboids. Never an angular sweep. */
public final class TranslationSweep {
    private static final double MAX_COORDINATE = 64_000_000.0;

    private TranslationSweep() {
    }

    /** Immutable prepared OBB; the enclosing AABB is only a broad-phase bound. */
    public static final class Box {
        private final Vec3 center;
        private final Vec3 half;
        private final Vec3[] axes;
        private final AABB bounds;

        private Box(Vec3 center, Vec3 half, Vec3[] axes) {
            this.center = vector(center);
            this.half = half;
            this.axes = axes;
            double x = radius(new Vec3(1, 0, 0));
            double y = radius(new Vec3(0, 1, 0));
            double z = radius(new Vec3(0, 0, 1));
            bounds = new AABB(center.x - x, center.y - y, center.z - z,
                    center.x + x, center.y + y, center.z + z);
            requireBox(bounds);
        }

        public static Box of(AABB localBox, StructurePose pose) {
            requireBox(localBox);
            Objects.requireNonNull(pose, "pose");
            return new Box(pose.localToWorldPoint(localBox.getCenter()),
                    new Vec3(localBox.getXsize() * 0.5, localBox.getYsize() * 0.5,
                            localBox.getZsize() * 0.5),
                    new Vec3[]{pose.localToWorldDirection(new Vec3(1, 0, 0)),
                            pose.localToWorldDirection(new Vec3(0, 1, 0)),
                            pose.localToWorldDirection(new Vec3(0, 0, 1))});
        }

        public static Box axisAligned(AABB worldBox) {
            return of(worldBox, StructurePose.identity(Vec3.ZERO));
        }

        public Box translated(Vec3 displacement) {
            return new Box(center.add(vector(displacement)), half, axes);
        }

        public AABB bounds() {
            return bounds;
        }

        public AABB sweptBounds(Vec3 displacement) {
            vector(displacement);
            return bounds.minmax(bounds.move(displacement));
        }

        double minimumProjection(Vec3 axis) { return center.dot(axis) - radius(axis); }
        double maximumProjection(Vec3 axis) { return center.dot(axis) + radius(axis); }

        /** SAT axes, copied for continuous contact admission; geometry itself stays immutable. */
        java.util.List<Vec3> separatingAxes(Box other) {
            var result = new java.util.ArrayList<Vec3>(15);
            result.addAll(java.util.List.of(axes));
            result.addAll(java.util.List.of(other.axes));
            for (var first : axes) for (var second : other.axes) {
                var cross = first.cross(second);
                double length = cross.length();
                if (length > 0) result.add(cross.scale(1 / length));
            }
            return result;
        }

        private double radius(Vec3 axis) {
            return Math.abs(axis.dot(axes[0])) * half.x
                    + Math.abs(axis.dot(axes[1])) * half.y
                    + Math.abs(axis.dot(axes[2])) * half.z;
        }
    }

    /** Normal points out of the obstacle. Initial penetration is reported, never auto-corrected. */
    public record Hit(double timeOfImpact, Vec3 normal, boolean initialOverlap) {
        public Hit {
            if (!Double.isFinite(timeOfImpact) || timeOfImpact < 0.0 || timeOfImpact > 1.0) {
                throw new IllegalArgumentException("Invalid time of impact");
            }
            vector(normal);
            // Vec3 equality distinguishes signed zero. Keep diagnostics and immutable results
            // canonical without changing the tested geometry or the direction of any normal.
            normal = new Vec3(normal.x == 0.0 ? 0.0 : normal.x,
                    normal.y == 0.0 ? 0.0 : normal.y, normal.z == 0.0 ? 0.0 : normal.z);
        }
    }

    /**
     * Tests the entire segment, including arbitrarily thin intervening obstacles. Boundary-only
     * contact is allowed: sliding along/leaving a face is free, entering it blocks at t=0, and
     * stopping on it at t=1 is free. No collision hull is shrunk and no path samples are skipped.
     */
    public static Optional<Hit> firstHit(Box moving, Vec3 displacement, Box stationary) {
        Objects.requireNonNull(moving, "moving");
        Objects.requireNonNull(stationary, "stationary");
        vector(displacement);
        if (!moving.sweptBounds(displacement).intersects(stationary.bounds)) return Optional.empty();
        var range = new Range();
        var distance = stationary.center.subtract(moving.center);
        for (var axis : moving.axes) {
            if (!axis(moving, stationary, distance, displacement, axis, range)) return Optional.empty();
        }
        for (var axis : stationary.axes) {
            if (!axis(moving, stationary, distance, displacement, axis, range)) return Optional.empty();
        }
        for (var first : moving.axes) {
            for (var second : stationary.axes) {
                var cross = first.cross(second);
                double length = Math.hypot(Math.hypot(cross.x, cross.y), cross.z);
                // Exactly parallel edges contribute no separating axis. Keep near-parallel axes.
                if (length > 0.0 && !axis(moving, stationary, distance, displacement,
                        new Vec3(cross.x / length, cross.y / length, cross.z / length), range)) return Optional.empty();
            }
        }
        if (range.initialOverlap) return Optional.of(new Hit(0.0, range.overlapNormal, true));
        if (Math.max(0.0, range.enter) >= Math.min(1.0, range.exit)) return Optional.empty();
        return Optional.of(new Hit(Math.max(0.0, range.enter), range.enterNormal, false));
    }

    private static boolean axis(Box moving, Box stationary, Vec3 distance, Vec3 displacement,
                                Vec3 axis, Range range) {
        double radius = moving.radius(axis) + stationary.radius(axis);
        double separation = distance.dot(axis);
        double speed = displacement.dot(axis);
        double depth = radius - Math.abs(separation);
        if (depth <= 0.0) range.initialOverlap = false;
        if (depth < range.overlap) {
            range.overlap = depth;
            range.overlapNormal = axis.scale(separation >= 0.0 ? -1.0 : 1.0);
        }
        if (speed == 0.0) return depth > 0.0;
        double first = (separation - radius) / speed;
        double second = (separation + radius) / speed;
        if (first > second) {
            double swap = first;
            first = second;
            second = swap;
        }
        if (first > range.enter) {
            range.enter = first;
            range.enterNormal = axis.scale(-Math.signum(speed));
        }
        range.exit = Math.min(range.exit, second);
        return range.enter < range.exit;
    }

    static Vec3 vector(Vec3 value) {
        Objects.requireNonNull(value, "vector");
        if (!coordinate(value.x) || !coordinate(value.y) || !coordinate(value.z)) {
            throw new IllegalArgumentException("Planning vectors must be finite and within world bounds");
        }
        return value;
    }

    static void requireBox(AABB box) {
        Objects.requireNonNull(box, "box");
        vector(new Vec3(box.minX, box.minY, box.minZ));
        vector(new Vec3(box.maxX, box.maxY, box.maxZ));
        if (box.getXsize() <= 0.0 || box.getYsize() <= 0.0 || box.getZsize() <= 0.0) {
            throw new IllegalArgumentException("Collision cuboids must have positive volume");
        }
    }

    private static boolean coordinate(double value) {
        return Double.isFinite(value) && Math.abs(value) <= MAX_COORDINATE;
    }

    private static final class Range {
        private double enter = Double.NEGATIVE_INFINITY;
        private double exit = Double.POSITIVE_INFINITY;
        private boolean initialOverlap = true;
        private double overlap = Double.POSITIVE_INFINITY;
        private Vec3 enterNormal = Vec3.ZERO;
        private Vec3 overlapNormal = Vec3.ZERO;
    }
}
