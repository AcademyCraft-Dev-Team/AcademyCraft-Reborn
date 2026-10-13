package org.academy.internal.common.structure.physics;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Pure target-domain checks; the solver retains full control over actual body motion. */
final class NativeStructureTractionBounds {
    static final double MARGIN = 0.25;

    private NativeStructureTractionBounds() {}

    static boolean accepts(AABB region, AABB body, Vec3 point, Vec3 target, Vec3 velocity) {
        if (!finite(point) || !finite(target) || !finite(velocity)) return false;
        if (region.getXsize() <= MARGIN * 2 || region.getYsize() <= MARGIN * 2 || region.getZsize() <= MARGIN * 2) return false;
        var safe = region.deflate(MARGIN);
        if (safe.getXsize() <= 0 || safe.getYsize() <= 0 || safe.getZsize() <= 0) return false;
        var delta = target.subtract(point);
        return NativeStructurePhysics.contains(safe, body)
                && NativeStructurePhysics.contains(safe, body.move(delta))
                && NativeStructurePhysics.contains(safe, body.move(delta.add(velocity.scale(0.05))));
    }

    /**
     * Candidate region bounds with the same axis sizes, recentered over the body's required
     * travel so a bounded target beyond the current edge can still be accepted. Returns null
     * when the host limits cannot contain such a box or the target remains unacceptable.
     * Pure computation: it neither mutates a live region nor validates region separation.
     */
    static @Nullable AABB recentered(AABB region, AABB body, Vec3 point, Vec3 target, Vec3 velocity,
                                     double minimumY, double maximumY, AABB worldBorder) {
        if (!finite(point) || !finite(target) || !finite(velocity)) return null;
        double sizeX = region.getXsize(), sizeY = region.getYsize(), sizeZ = region.getZsize();
        if (worldBorder.getXsize() < sizeX || worldBorder.getZsize() < sizeZ
                || maximumY - minimumY < sizeY) return null;
        var delta = target.subtract(point);
        var occupied = body.minmax(body.move(delta)).minmax(body.move(delta.add(velocity.scale(0.05))));
        var center = occupied.getCenter();
        double halfX = sizeX / 2.0, halfY = sizeY / 2.0, halfZ = sizeZ / 2.0;
        double x = clamp(center.x, worldBorder.minX + halfX, worldBorder.maxX - halfX);
        double y = clamp(center.y, minimumY + halfY, maximumY - halfY);
        double z = clamp(center.z, worldBorder.minZ + halfZ, worldBorder.maxZ - halfZ);
        var candidate = new AABB(x - halfX, y - halfY, z - halfZ, x + halfX, y + halfY, z + halfZ);
        return accepts(candidate, body, point, target, velocity) ? candidate : null;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static boolean finite(Vec3 vector) {
        return vector != null && Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
}
