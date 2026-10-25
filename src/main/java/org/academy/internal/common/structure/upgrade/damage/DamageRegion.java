package org.academy.internal.common.structure.upgrade.damage;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;

/** Sphere tests in the cell's own frame; broad-phase bounds never count as occupied content. */
public final class DamageRegion {
    private DamageRegion() {}

    public static AABB bounds(Vec3 center, double radius) {
        return new AABB(center, center).inflate(Math.max(radius, 1.0e-6));
    }

    public static boolean intersects(Vec3 center, double radius, AABB box) {
        double x = Math.max(Math.max(box.minX - center.x, center.x - box.maxX), 0);
        double y = Math.max(Math.max(box.minY - center.y, center.y - box.maxY), 0);
        double z = Math.max(Math.max(box.minZ - center.z, center.z - box.maxZ), 0);
        return x * x + y * y + z * z <= radius * radius;
    }

    /** Legacy shockwave terrain semantics, including the zero-radius containing cell. */
    public static boolean hostCell(Vec3 center, double radius, BlockPos cell) {
        return radius == 0 ? cell.equals(BlockPos.containing(center))
                : Vec3.atCenterOf(cell).distanceToSqr(center) <= radius * radius;
    }

    public static Iterator<BlockPos> cells(Vec3 center, double radius, AABB envelope) {
        var region = bounds(center, radius);
        if (!region.intersects(envelope)) return java.util.Collections.emptyIterator();
        var area = region.intersect(envelope);
        if (area.getXsize() <= 0 || area.getYsize() <= 0 || area.getZsize() <= 0) return java.util.Collections.emptyIterator();
        // Lazy enumeration is essential: a radius-256 query must not allocate a 513^3 list.
        return BlockPos.betweenClosed(Mth.floor(area.minX), Mth.floor(area.minY), Mth.floor(area.minZ),
                Mth.floor(area.maxX), Mth.floor(area.maxY), Mth.floor(area.maxZ)).iterator();
    }
}
