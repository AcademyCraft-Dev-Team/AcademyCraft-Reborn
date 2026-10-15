package org.academy.internal.common.structure.sable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructureMotionState;
import org.academy.api.common.structure.StructurePhysicsControl;
import org.academy.api.common.structure.StructurePhysicsControls;

/** Unit conversion at the Sable boundary; contains no optional Sable linkage. */
final class SableControlForces {
    private SableControlForces() {}

    static Vec3 localImpulse(StructurePhysicsControl control, StructureMotionState motion, double seconds) {
        if (!Double.isFinite(seconds) || seconds <= 0 || seconds > 0.05) {
            throw new IllegalArgumentException("Physics substep must be finite and in (0, 0.05] seconds");
        }
        var worldImpulse = StructurePhysicsControls.force(control, motion).scale(seconds);
        return motion.pose().worldToLocalDirection(worldImpulse);
    }

    static Vec3 plotPoint(BlockPos plotOrigin, Vec3 localPoint) {
        // RapierPhysicsPipeline.applyImpulse subtracts the actual COM itself.
        return Vec3.atLowerCornerOf(plotOrigin).add(localPoint);
    }

    static boolean expired(int now, int renewedAt, int ticks) {
        // Difference remains correct across the signed server tick counter wrap.
        return Integer.compareUnsigned(now - renewedAt, ticks) >= 0;
    }
}
