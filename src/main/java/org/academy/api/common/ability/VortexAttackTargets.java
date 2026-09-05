package org.academy.api.common.ability;

import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Entity-independent landing layout. Terrain projection is supplied by the caller. */
public final class VortexAttackTargets {
    private VortexAttackTargets() { }

    /** Local -X front/back, then local +X front/back; +Z follows the horizontal heading. */
    public static List<Vec3> quadrilateral(Vec3 center, Vec3 heading, double halfWidth, double halfDepth) {
        var forward = new Vec3(heading.x, 0, heading.z);
        forward = forward.lengthSqr() < 1.0E-8 ? new Vec3(0, 0, 1) : forward.normalize();
        var side = new Vec3(forward.z, 0, -forward.x).scale(halfWidth);
        var depth = forward.scale(halfDepth);
        return List.of(center.subtract(side).add(depth), center.subtract(side).subtract(depth),
                center.add(side).add(depth), center.add(side).subtract(depth));
    }
}
