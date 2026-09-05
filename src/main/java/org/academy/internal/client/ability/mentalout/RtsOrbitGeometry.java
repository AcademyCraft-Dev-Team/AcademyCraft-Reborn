package org.academy.internal.client.ability.mentalout;

import net.minecraft.world.phys.Vec3;

/** Off-axis orbit: the pivot stays at the center of the RTS viewport, not the entire window. */
public final class RtsOrbitGeometry {
    private RtsOrbitGeometry() {}
    public static Vec3 centerRay(float pitch, float yaw, double rightOffset, double upOffset) {
        var forward = Vec3.directionFromRotation(pitch, yaw);
        var right = forward.cross(new Vec3(0, 1, 0)).normalize();
        var up = right.cross(forward).normalize();
        return forward.add(right.scale(rightOffset)).add(up.scale(upOffset)).normalize();
    }
    public static Vec3 cameraPosition(Vec3 pivot, double distance, float pitch, float yaw,
                                      double rightOffset, double upOffset) {
        return pivot.subtract(centerRay(pitch, yaw, rightOffset, upOffset).scale(distance));
    }
}
