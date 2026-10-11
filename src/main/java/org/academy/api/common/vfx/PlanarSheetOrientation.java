package org.academy.api.common.vfx;

import net.minecraft.world.phys.Vec3;

/** Roll about a sheet normal, using the same stable tangent basis as the sheet vertex shader. */
public final class PlanarSheetOrientation {
    private PlanarSheetOrientation() { }

    public static float roll(Vec3 normal, Vec3 right) {
        var n = normal.normalize();
        var reference = Math.abs(n.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        var tangent = reference.cross(n).normalize();
        var up = n.cross(tangent);
        return (float) Math.atan2(right.dot(up), right.dot(tangent));
    }
}
