package org.academy.api.common.entitycontrol;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Shared block-grid geometry for previews, picking and authoritative work regions. */
public final class WorkSelection {
    private WorkSelection() {
    }

    public static Vec3 intersectHorizontal(Vec3 origin, Vec3 direction, double height) {
        if (Math.abs(direction.y) < 1.0e-6) return null;
        var distance = (height - origin.y) / direction.y;
        if (!Double.isFinite(distance) || distance < 0 || distance > 65536) return null;
        return origin.add(direction.scale(distance));
    }

    public static AABB bounds(BlockPos first, BlockPos last, int height, int offset) {
        if (height < 1) throw new IllegalArgumentException("Selection height must be positive");
        var top = (double) first.getY() + offset + 1;
        return new AABB(Math.min(first.getX(), last.getX()), top - height,
                Math.min(first.getZ(), last.getZ()), Math.max(first.getX(), last.getX()) + 1,
                top, Math.max(first.getZ(), last.getZ()) + 1);
    }

    /** Inclusive opposite vertices from GUI coordinates; rejects incomplete and out-of-world input. */
    public static BlockWorkRegion parseVertices(Identifier dimension, String[] coordinates, int minY, int maxY) {
        if (coordinates.length != 6) throw new IllegalArgumentException("Two XYZ vertices required");
        var values = java.util.Arrays.stream(coordinates).map(String::trim).mapToInt(Integer::parseInt).toArray();
        var region = new BlockWorkRegion(dimension, new BlockPos(values[0], values[1], values[2]),
                new BlockPos(values[3], values[4], values[5]));
        if (region.minimum().getY() < minY || region.maximum().getY() >= maxY) {
            throw new IllegalArgumentException("Region outside build height");
        }
        return region;
    }

    public static BlockWorkRegion region(Identifier dimension, AABB bounds) {
        return new BlockWorkRegion(dimension,
                BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(bounds.maxX - 1, bounds.maxY - 1, bounds.maxZ - 1));
    }
}
