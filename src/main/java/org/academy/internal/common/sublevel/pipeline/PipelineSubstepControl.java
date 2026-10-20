package org.academy.internal.common.sublevel.pipeline;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructureMotionState;
import org.academy.api.common.structure.StructurePhysicsControl;
import org.academy.api.common.structure.StructurePhysicsControls;
import org.academy.api.common.structure.StructurePipeline;

/**
 * PIPE-04 draft controller. The returned force/torque is instantaneous and belongs to one real
 * solver substep; it is never multiplied by an outer-tick delta. Gravity compensation, if any,
 * remains the caller's command responsibility.
 */
public final class PipelineSubstepControl {
    public static final double MAX_SUBSTEP_SECONDS = 0.25;

    public StructurePipeline.SubstepForce control(StructurePhysicsControl command,
                                                  StructureMotionState motion, double seconds) {
        if (command == null) throw new IllegalArgumentException("Control command is required");
        if (motion == null) throw new IllegalArgumentException("Motion observation is required");
        if (!Double.isFinite(seconds) || seconds <= 0.0 || seconds > MAX_SUBSTEP_SECONDS) {
            throw new IllegalArgumentException("Substep seconds must be finite and in (0, " + MAX_SUBSTEP_SECONDS + "]");
        }
        var worldPoint = motion.pose().localToWorldPoint(command.localPoint());
        var force = StructurePhysicsControls.force(command, motion);
        if (!finite(force)) throw new IllegalStateException("Controller produced a non-finite force");
        var centerOfMass = motion.pose().localToWorldPoint(motion.localCenterOfMass());
        var torque = worldPoint.subtract(centerOfMass).cross(force);
        if (!finite(torque)) throw new IllegalStateException("Controller produced a non-finite torque");
        return new StructurePipeline.SubstepForce(force, torque, worldPoint);
    }

    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}
