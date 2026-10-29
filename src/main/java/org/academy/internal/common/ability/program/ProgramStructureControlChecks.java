package org.academy.internal.common.ability.program;

import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.ability.program.*;
import org.academy.api.common.structure.*;

/** Non-mutating body, range and exact lease observation for suspended movement nodes. */
public final class ProgramStructureControlChecks {
    private ProgramStructureControlChecks() {}
    public static ProgramStructureOperationResult inspect(ServerPlayer player, ProgramStructureControl control, double range) {
        var handle = StructureSpaces.resolve(player.level().getServer(), control.reference()).orElse(null);
        if (handle == null || handle.lifecycle() != StructureLifecycle.ACTIVE
                || !handle.runtimeId().equals(control.structureRuntimeId())
                || !player.level().dimension().identifier().equals(control.reference().dimension())) {
            return ProgramStructureOperationResult.rejected("structure_unavailable");
        }
        if (!StructureDirectControl.matches(control.reference(), player.getUUID(), control.controlId(), control.structureRuntimeId())) {
            return ProgramStructureOperationResult.rejected("control_lease_lost");
        }
        var motion = StructureDynamics.motion(player.level(), control.reference()).orElse(null);
        if (motion == null || motion.pose().localToWorldPoint(motion.localCenterOfMass())
                .distanceToSqr(player.position()) > range * range) {
            return ProgramStructureOperationResult.rejected("structure_out_of_range");
        }
        return ProgramStructureOperationResult.completed("control_active");
    }
}
