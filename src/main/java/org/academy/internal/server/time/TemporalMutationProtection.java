package org.academy.internal.server.time;

import net.minecraft.world.entity.Entity;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;

/** External hard-stop immunity also covers repeated position, velocity and orientation writes. */
public final class TemporalMutationProtection {
    private TemporalMutationProtection() {}

    public static boolean shouldBlock(Entity entity) {
        if (!TemporalBoundaryProtection.isProtected(entity)) return false;
        var source = EntityMotionGuard.currentMotionSourceEntity();
        return source != null ? source != entity : TemporalControlProvenance.isExternalControl();
    }
}
