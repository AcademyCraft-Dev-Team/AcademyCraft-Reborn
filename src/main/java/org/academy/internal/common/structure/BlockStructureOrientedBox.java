package org.academy.internal.common.structure;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;

import java.util.Objects;

/** One rigidly transformed collision cuboid, retaining its orientation for exact overlap tests. */
public final class BlockStructureOrientedBox {
    private static final double CONTACT_EPSILON = 1.0E-7;
    private static final double PARALLEL_AXIS_SQUARED = 1.0E-24;
    private final Vec3 center;
    private final Vec3 halfSize;
    private final Vec3 axisX;
    private final Vec3 axisY;
    private final Vec3 axisZ;
    private final AABB bounds;

    private BlockStructureOrientedBox(AABB localBox, StructurePose pose) {
        requireFinite(localBox);
        center = pose.localToWorldPoint(localBox.getCenter());
        halfSize = new Vec3(localBox.getXsize() / 2, localBox.getYsize() / 2, localBox.getZsize() / 2);
        axisX = pose.localToWorldDirection(new Vec3(1, 0, 0));
        axisY = pose.localToWorldDirection(new Vec3(0, 1, 0));
        axisZ = pose.localToWorldDirection(new Vec3(0, 0, 1));
        double x = radius(new Vec3(1, 0, 0));
        double y = radius(new Vec3(0, 1, 0));
        double z = radius(new Vec3(0, 0, 1));
        bounds = new AABB(center.x - x, center.y - y, center.z - z,
                center.x + x, center.y + y, center.z + z);
        requireFinite(bounds);
    }

    public static BlockStructureOrientedBox transform(AABB localBox, StructurePose pose) {
        return new BlockStructureOrientedBox(Objects.requireNonNull(localBox), Objects.requireNonNull(pose));
    }

    public static BlockStructureOrientedBox axisAligned(AABB worldBox) {
        return transform(worldBox, StructurePose.identity(Vec3.ZERO));
    }

    public AABB bounds() {
        return bounds;
    }

    /** SAT over both sets of face normals and their nine edge cross products. Touching is allowed. */
    public boolean intersects(BlockStructureOrientedBox other) {
        Objects.requireNonNull(other);
        if (empty() || other.empty() || !bounds.intersects(other.bounds)) return false;
        var displacement = other.center.subtract(center);
        var firstAxes = new Vec3[] {axisX, axisY, axisZ};
        var secondAxes = new Vec3[] {other.axisX, other.axisY, other.axisZ};
        for (var axis : firstAxes) if (separated(other, displacement, axis)) return false;
        for (var axis : secondAxes) if (separated(other, displacement, axis)) return false;
        for (var first : firstAxes) {
            for (var second : secondAxes) {
                var axis = first.cross(second);
                double squaredLength = axis.lengthSqr();
                if (squaredLength > PARALLEL_AXIS_SQUARED
                        && separated(other, displacement, axis.scale(1 / Math.sqrt(squaredLength)))) return false;
            }
        }
        return true;
    }

    private boolean separated(BlockStructureOrientedBox other, Vec3 displacement, Vec3 axis) {
        return Math.abs(displacement.dot(axis)) >= radius(axis) + other.radius(axis) - CONTACT_EPSILON;
    }

    private double radius(Vec3 axis) {
        return Math.abs(axis.dot(axisX)) * halfSize.x
                + Math.abs(axis.dot(axisY)) * halfSize.y
                + Math.abs(axis.dot(axisZ)) * halfSize.z;
    }

    private boolean empty() {
        return halfSize.x <= 0 || halfSize.y <= 0 || halfSize.z <= 0;
    }

    private static void requireFinite(AABB box) {
        if (!Double.isFinite(box.minX) || !Double.isFinite(box.minY) || !Double.isFinite(box.minZ)
                || !Double.isFinite(box.maxX) || !Double.isFinite(box.maxY) || !Double.isFinite(box.maxZ)) {
            throw new IllegalArgumentException("Collision cuboids must be finite");
        }
    }
}
