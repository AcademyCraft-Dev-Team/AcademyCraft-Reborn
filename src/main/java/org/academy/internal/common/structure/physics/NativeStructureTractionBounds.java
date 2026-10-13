package org.academy.internal.common.structure.physics;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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

    private static boolean finite(Vec3 vector) {
        return vector != null && Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
}
