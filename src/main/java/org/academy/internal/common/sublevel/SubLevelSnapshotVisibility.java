package org.academy.internal.common.sublevel;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Interest follows the visible structure bounds, with hysteresis to avoid full-state churn. */
public final class SubLevelSnapshotVisibility {
    public static final double ENTER_DISTANCE = 256;
    public static final double LEAVE_DISTANCE = 272;

    private SubLevelSnapshotVisibility() {}

    public static boolean interested(Vec3 viewer, AABB bounds, boolean tracked) {
        double x = Math.max(bounds.minX - viewer.x, Math.max(0, viewer.x - bounds.maxX));
        double y = Math.max(bounds.minY - viewer.y, Math.max(0, viewer.y - bounds.maxY));
        double z = Math.max(bounds.minZ - viewer.z, Math.max(0, viewer.z - bounds.maxZ));
        double distance = tracked ? LEAVE_DISTANCE : ENTER_DISTANCE;
        return x * x + y * y + z * z <= distance * distance;
    }
}
