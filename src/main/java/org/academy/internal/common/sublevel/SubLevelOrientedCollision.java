package org.academy.internal.common.sublevel;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.academy.internal.common.structure.NativeStructureCollisionBoxes;

import java.util.List;

/**
 * Exact oriented contact math for plot-backed structures. A local collision cuboid keeps its
 * orientation after the pose transform, so an entity is stopped by the real rotated surface
 * instead of the axis-aligned hull of that surface. All methods are pure and allocation-light.
 */
public final class SubLevelOrientedCollision {
    /** Gap kept between an entity and the surface it was just stopped by. */
    public static final double CONTACT_GAP = 1.0E-6;
    /** Overlap below this is treated as resting contact, not as a body stuck inside a surface. */
    public static final double PENETRATION_EPSILON = 2.0E-3;
    private static final double DIRECTION_EPSILON = 1.0E-12;
    private static final double PARALLEL_AXIS_SQUARED = 1.0E-24;
    private static final double MAX_DEPENETRATION = 0.55;

    private SubLevelOrientedCollision() {
    }

    /** One oriented collision cuboid in world space. Axes are unit length. */
    public record Box(double centerX, double centerY, double centerZ,
                      double halfX, double halfY, double halfZ,
                      double axisXX, double axisXY, double axisXZ,
                      double axisYX, double axisYY, double axisYZ,
                      double axisZX, double axisZY, double axisZZ,
                      AABB bounds) {
        public static Box of(AABB localBox, StructurePose pose) {
            var center = pose.localToWorldPoint(localBox.getCenter());
            var x = pose.localToWorldDirection(new Vec3(1, 0, 0));
            var y = pose.localToWorldDirection(new Vec3(0, 1, 0));
            var z = pose.localToWorldDirection(new Vec3(0, 0, 1));
            return new Box(center.x, center.y, center.z,
                    localBox.getXsize() * 0.5, localBox.getYsize() * 0.5, localBox.getZsize() * 0.5,
                    x.x, x.y, x.z, y.x, y.y, y.z, z.x, z.y, z.z,
                    NativeStructureCollisionBoxes.transformBox(localBox, pose));
        }

        public double radius(double nx, double ny, double nz) {
            return Math.abs(nx * axisXX + ny * axisXY + nz * axisXZ) * halfX
                    + Math.abs(nx * axisYX + ny * axisYY + nz * axisYZ) * halfY
                    + Math.abs(nx * axisZX + ny * axisZY + nz * axisZZ) * halfZ;
        }
    }

    /**
     * One axis clip. movement is the displacement allowed before the first contact, clamped to
     * the requested delta. blocked is true for an actual surface contact (including resting or
     * already-overlapping contact). toi is a fraction of the requested delta in [0, 1].
     */
    public record Clip(double movement, boolean blocked, double toi, Vec3 point, Vec3 normal) {
        public static Clip free(double movement) {
            return new Clip(movement, false, 1.0, Vec3.ZERO, Vec3.ZERO);
        }
    }

    public static Clip clip(Box box, AABB entity, Direction.Axis axis, double delta) {
        if (Math.abs(delta) <= DIRECTION_EPSILON) return Clip.free(delta);
        double mx = 0.0, my = 0.0, mz = 0.0;
        switch (axis) {
            case X -> mx = 1.0;
            case Y -> my = 1.0;
            case Z -> mz = 1.0;
        }
        var range = new Range();
        if (!accumulate(box, entity, mx, my, mz, delta, range)) return Clip.free(delta);
        if (range.enter >= 1.0) return Clip.free(delta);
        if (range.exit <= 0.0) return Clip.free(delta);
        if (range.enter < 0.0) {
            var penetration = penetration(box, entity);
            // An overlap must not trap an entity that is sliding along or leaving its nearest
            // surface. Inward motion remains blocked until the bounded correction separates it.
            if (penetration != null && delta * (mx * penetration.normalX
                    + my * penetration.normalY + mz * penetration.normalZ) >= -DIRECTION_EPSILON) {
                return Clip.free(delta);
            }
        }
        double travel = range.enter > 0.0 ? range.enter * delta : 0.0;
        double length = Math.sqrt(range.enterX * range.enterX + range.enterY * range.enterY
                + range.enterZ * range.enterZ);
        double sign = -Math.signum(delta * (mx * range.enterX + my * range.enterY + mz * range.enterZ));
        var normal = new Vec3(range.enterX, range.enterY, range.enterZ).scale(sign / length);
        var contact = new Vec3((entity.minX + entity.maxX) * 0.5 + mx * travel,
                (entity.minY + entity.maxY) * 0.5 + my * travel,
                (entity.minZ + entity.maxZ) * 0.5 + mz * travel).subtract(
                Math.signum(normal.x) * entity.getXsize() * 0.5,
                Math.signum(normal.y) * entity.getYsize() * 0.5,
                Math.signum(normal.z) * entity.getZsize() * 0.5);
        if (range.enter <= 0.0) return new Clip(0.0, true, 0.0, contact, normal);
        double allowed = range.enter * delta;
        allowed -= Math.signum(delta) * CONTACT_GAP;
        if (Math.abs(allowed) > Math.abs(delta) || Math.signum(allowed) != Math.signum(delta)) {
            allowed = 0.0;
        }
        return new Clip(allowed, true, range.enter, contact, normal);
    }

    /** Minimum translation that separates an existing overlap; ZERO when already separated. */
    public static Vec3 depenetration(Box box, AABB entity) {
        var minimum = penetration(box, entity);
        if (minimum == null || minimum.overlap <= PENETRATION_EPSILON) return Vec3.ZERO;
        double depth = Math.min(minimum.overlap + CONTACT_GAP, MAX_DEPENETRATION);
        return new Vec3(minimum.normalX * depth, minimum.normalY * depth, minimum.normalZ * depth);
    }

    private static Range penetration(Box box, AABB entity) {
        double ex = (entity.minX + entity.maxX) * 0.5 - box.centerX;
        double ey = (entity.minY + entity.maxY) * 0.5 - box.centerY;
        double ez = (entity.minZ + entity.maxZ) * 0.5 - box.centerZ;
        double hx = (entity.maxX - entity.minX) * 0.5;
        double hy = (entity.maxY - entity.minY) * 0.5;
        double hz = (entity.maxZ - entity.minZ) * 0.5;
        var minimum = new Range();
        minimum.overlap = Double.POSITIVE_INFINITY;
        return overlap(box, ex, ey, ez, hx, hy, hz, minimum) ? minimum : null;
    }

    /** True when the actual world-space footprint touches an upward-facing surface. */
    public static boolean supports(List<AABB> localBoxes, StructurePose pose, AABB entity, double tolerance) {
        if (localBoxes.isEmpty() || !Double.isFinite(tolerance)) return false;
        double safeTolerance = Math.max(0.0, tolerance);
        double startY = entity.minY + safeTolerance + CONTACT_GAP;
        var feet = new AABB(entity.minX, startY, entity.minZ, entity.maxX,
                startY + CONTACT_GAP, entity.maxZ);
        for (var box : localBoxes) {
            var contact = clip(Box.of(box, pose), feet, Direction.Axis.Y,
                    -2.0 * (safeTolerance + CONTACT_GAP));
            if (contact.blocked() && contact.normal().y > 0.5) return true;
        }
        return false;
    }

    public static Vec3 axisNormal(Direction.Axis axis, double sign) {
        return switch (axis) {
            case X -> new Vec3(sign, 0, 0);
            case Y -> new Vec3(0, sign, 0);
            case Z -> new Vec3(0, 0, sign);
        };
    }

    private static boolean accumulate(Box box, AABB entity, double mx, double my, double mz,
                                      double delta, Range range) {
        double ex = (entity.minX + entity.maxX) * 0.5 - box.centerX;
        double ey = (entity.minY + entity.maxY) * 0.5 - box.centerY;
        double ez = (entity.minZ + entity.maxZ) * 0.5 - box.centerZ;
        double hx = (entity.maxX - entity.minX) * 0.5;
        double hy = (entity.maxY - entity.minY) * 0.5;
        double hz = (entity.maxZ - entity.minZ) * 0.5;
        if (!axis(box, ex, ey, ez, hx, hy, hz, 1, 0, 0, mx, my, mz, delta, range)) return false;
        if (!axis(box, ex, ey, ez, hx, hy, hz, 0, 1, 0, mx, my, mz, delta, range)) return false;
        if (!axis(box, ex, ey, ez, hx, hy, hz, 0, 0, 1, mx, my, mz, delta, range)) return false;
        if (!axis(box, ex, ey, ez, hx, hy, hz, box.axisXX, box.axisXY, box.axisXZ, mx, my, mz, delta, range)) return false;
        if (!axis(box, ex, ey, ez, hx, hy, hz, box.axisYX, box.axisYY, box.axisYZ, mx, my, mz, delta, range)) return false;
        if (!axis(box, ex, ey, ez, hx, hy, hz, box.axisZX, box.axisZY, box.axisZZ, mx, my, mz, delta, range)) return false;
        if (!cross(box, ex, ey, ez, hx, hy, hz, 1, 0, 0, box.axisXX, box.axisXY, box.axisXZ, mx, my, mz, delta, range)) return false;
        if (!cross(box, ex, ey, ez, hx, hy, hz, 1, 0, 0, box.axisYX, box.axisYY, box.axisYZ, mx, my, mz, delta, range)) return false;
        if (!cross(box, ex, ey, ez, hx, hy, hz, 1, 0, 0, box.axisZX, box.axisZY, box.axisZZ, mx, my, mz, delta, range)) return false;
        if (!cross(box, ex, ey, ez, hx, hy, hz, 0, 1, 0, box.axisXX, box.axisXY, box.axisXZ, mx, my, mz, delta, range)) return false;
        if (!cross(box, ex, ey, ez, hx, hy, hz, 0, 1, 0, box.axisYX, box.axisYY, box.axisYZ, mx, my, mz, delta, range)) return false;
        if (!cross(box, ex, ey, ez, hx, hy, hz, 0, 1, 0, box.axisZX, box.axisZY, box.axisZZ, mx, my, mz, delta, range)) return false;
        if (!cross(box, ex, ey, ez, hx, hy, hz, 0, 0, 1, box.axisXX, box.axisXY, box.axisXZ, mx, my, mz, delta, range)) return false;
        if (!cross(box, ex, ey, ez, hx, hy, hz, 0, 0, 1, box.axisYX, box.axisYY, box.axisYZ, mx, my, mz, delta, range)) return false;
        return cross(box, ex, ey, ez, hx, hy, hz, 0, 0, 1, box.axisZX, box.axisZY, box.axisZZ, mx, my, mz, delta, range);
    }

    private static boolean cross(Box box, double ex, double ey, double ez, double hx, double hy, double hz,
                                 double wx, double wy, double wz, double bx, double by, double bz,
                                 double mx, double my, double mz, double delta, Range range) {
        double nx = wy * bz - wz * by;
        double ny = wz * bx - wx * bz;
        double nz = wx * by - wy * bx;
        if (nx * nx + ny * ny + nz * nz <= PARALLEL_AXIS_SQUARED) return true;
        return axis(box, ex, ey, ez, hx, hy, hz, nx, ny, nz, mx, my, mz, delta, range);
    }

    /** Accumulates the swept projection interval for one separating axis. */
    private static boolean axis(Box box, double ex, double ey, double ez, double hx, double hy, double hz,
                                double nx, double ny, double nz,
                                double mx, double my, double mz, double delta, Range range) {
        double radius = hx * Math.abs(nx) + hy * Math.abs(ny) + hz * Math.abs(nz) + box.radius(nx, ny, nz);
        double distance = ex * nx + ey * ny + ez * nz;
        double speed = delta * (mx * nx + my * ny + mz * nz);
        if (Math.abs(speed) <= DIRECTION_EPSILON) {
            // Never overlaps on this axis: the two projections are permanently separated.
            return Math.abs(distance) < radius;
        }
        double first = (-radius - distance) / speed;
        double second = (radius - distance) / speed;
        if (first > second) {
            double swap = first;
            first = second;
            second = swap;
        }
        if (first > range.enter) {
            range.enter = first;
            range.enterX = nx;
            range.enterY = ny;
            range.enterZ = nz;
        }
        if (second < range.exit) range.exit = second;
        return range.enter <= range.exit;
    }

    private static boolean overlap(Box box, double ex, double ey, double ez, double hx, double hy, double hz,
                                   Range range) {
        if (!overlapAxis(box, ex, ey, ez, hx, hy, hz, 1, 0, 0, range)) return false;
        if (!overlapAxis(box, ex, ey, ez, hx, hy, hz, 0, 1, 0, range)) return false;
        if (!overlapAxis(box, ex, ey, ez, hx, hy, hz, 0, 0, 1, range)) return false;
        if (!overlapAxis(box, ex, ey, ez, hx, hy, hz, box.axisXX, box.axisXY, box.axisXZ, range)) return false;
        if (!overlapAxis(box, ex, ey, ez, hx, hy, hz, box.axisYX, box.axisYY, box.axisYZ, range)) return false;
        if (!overlapAxis(box, ex, ey, ez, hx, hy, hz, box.axisZX, box.axisZY, box.axisZZ, range)) return false;
        if (!overlapCross(box, ex, ey, ez, hx, hy, hz, 1, 0, 0, box.axisXX, box.axisXY, box.axisXZ, range)) return false;
        if (!overlapCross(box, ex, ey, ez, hx, hy, hz, 1, 0, 0, box.axisYX, box.axisYY, box.axisYZ, range)) return false;
        if (!overlapCross(box, ex, ey, ez, hx, hy, hz, 1, 0, 0, box.axisZX, box.axisZY, box.axisZZ, range)) return false;
        if (!overlapCross(box, ex, ey, ez, hx, hy, hz, 0, 1, 0, box.axisXX, box.axisXY, box.axisXZ, range)) return false;
        if (!overlapCross(box, ex, ey, ez, hx, hy, hz, 0, 1, 0, box.axisYX, box.axisYY, box.axisYZ, range)) return false;
        if (!overlapCross(box, ex, ey, ez, hx, hy, hz, 0, 1, 0, box.axisZX, box.axisZY, box.axisZZ, range)) return false;
        if (!overlapCross(box, ex, ey, ez, hx, hy, hz, 0, 0, 1, box.axisXX, box.axisXY, box.axisXZ, range)) return false;
        if (!overlapCross(box, ex, ey, ez, hx, hy, hz, 0, 0, 1, box.axisYX, box.axisYY, box.axisYZ, range)) return false;
        return overlapCross(box, ex, ey, ez, hx, hy, hz, 0, 0, 1, box.axisZX, box.axisZY, box.axisZZ, range);
    }

    private static boolean overlapCross(Box box, double ex, double ey, double ez, double hx, double hy, double hz,
                                        double wx, double wy, double wz, double bx, double by, double bz, Range range) {
        double nx = wy * bz - wz * by;
        double ny = wz * bx - wx * bz;
        double nz = wx * by - wy * bx;
        if (nx * nx + ny * ny + nz * nz <= PARALLEL_AXIS_SQUARED) return true;
        return overlapAxis(box, ex, ey, ez, hx, hy, hz, nx, ny, nz, range);
    }

    private static boolean overlapAxis(Box box, double ex, double ey, double ez, double hx, double hy, double hz,
                                       double nx, double ny, double nz, Range range) {
        double radius = hx * Math.abs(nx) + hy * Math.abs(ny) + hz * Math.abs(nz) + box.radius(nx, ny, nz);
        double distance = ex * nx + ey * ny + ez * nz;
        double overlap = radius - Math.abs(distance);
        if (overlap <= 0.0) return false;
        // Cross-product axes are not unit vectors, especially near parallel orientations.
        // Compare world distances, otherwise a tiny axis wins with an equally tiny false depth.
        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        overlap /= length;
        if (overlap < range.overlap) {
            range.overlap = overlap;
            double sign = distance < 0.0 ? -1.0 : 1.0;
            range.normalX = nx / length * sign;
            range.normalY = ny / length * sign;
            range.normalZ = nz / length * sign;
        }
        return true;
    }

    private static final class Range {
        private double enter = Double.NEGATIVE_INFINITY;
        private double exit = Double.POSITIVE_INFINITY;
        private double enterX, enterY, enterZ;
        private double overlap;
        private double normalX, normalY, normalZ;
    }
}
