package org.academy.internal.common.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.academy.api.common.structure.StructurePhysics;

import java.util.List;

/** Shared bounded physics-region sizing for captures that must create a body before commit. */
public final class StructureCapturePhysics {
    public static final double DEFAULT_MARGIN = 6.0;

    private StructureCapturePhysics() {}

    /**
     * Smallest usable region around the captured cells, clamped to the host height and world
     * border. Returns null when even the required box cannot fit the 24-block axis limit.
     */
    public static AABB regionBounds(ServerLevel level, List<BlockPos> positions) {
        if (positions == null || positions.isEmpty()) return null;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (var position : positions) {
            minX = Math.min(minX, position.getX()); maxX = Math.max(maxX, position.getX());
            minY = Math.min(minY, position.getY()); maxY = Math.max(maxY, position.getY());
            minZ = Math.min(minZ, position.getZ()); maxZ = Math.max(maxZ, position.getZ());
        }
        var required = new AABB(minX, minY, minZ, maxX + 1.0, maxY + 1.0, maxZ + 1.0);
        double marginX = Math.min(DEFAULT_MARGIN, (StructurePhysics.MAX_REGION_AXIS - required.getXsize()) / 2.0);
        double marginY = Math.min(DEFAULT_MARGIN, (StructurePhysics.MAX_REGION_AXIS - required.getYsize()) / 2.0);
        double marginZ = Math.min(DEFAULT_MARGIN, (StructurePhysics.MAX_REGION_AXIS - required.getZsize()) / 2.0);
        if (marginX < 0 || marginY < 0 || marginZ < 0) return null;
        var candidate = new AABB(required.minX - marginX, required.minY - marginY, required.minZ - marginZ,
                required.maxX + marginX, required.maxY + marginY, required.maxZ + marginZ);
        var border = level.getWorldBorder();
        var limits = new AABB(border.getMinX(), level.getMinY(), border.getMinZ(),
                border.getMaxX(), level.getMaxY(), border.getMaxZ());
        var bounded = clampInside(candidate, level.getMinY(), level.getMaxY(), limits);
        return bounded != null && contains(bounded, required) ? bounded : null;
    }

    private static AABB clampInside(AABB bounds, double minimumY, double maximumY, AABB limits) {
        double sizeX = bounds.getXsize(), sizeY = bounds.getYsize(), sizeZ = bounds.getZsize();
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) return null;
        if (limits.getXsize() < sizeX || limits.getZsize() < sizeZ || maximumY - minimumY < sizeY) return null;
        var center = bounds.getCenter();
        double x = Mth.clamp(center.x, limits.minX + sizeX / 2.0, limits.maxX - sizeX / 2.0);
        double y = Mth.clamp(center.y, minimumY + sizeY / 2.0, maximumY - sizeY / 2.0);
        double z = Mth.clamp(center.z, limits.minZ + sizeZ / 2.0, limits.maxZ - sizeZ / 2.0);
        return new AABB(x - sizeX / 2.0, y - sizeY / 2.0, z - sizeZ / 2.0,
                x + sizeX / 2.0, y + sizeY / 2.0, z + sizeZ / 2.0);
    }

    private static boolean contains(AABB outer, AABB inner) {
        return outer.minX <= inner.minX && outer.minY <= inner.minY && outer.minZ <= inner.minZ
                && outer.maxX >= inner.maxX && outer.maxY >= inner.maxY && outer.maxZ >= inner.maxZ;
    }
}
