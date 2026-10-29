package org.academy.api.common.ability.program;

import java.util.List;
import java.util.UUID;

import static org.academy.api.common.ability.program.ProgramStructureOrientationRuntime.*;

/** Scoped group actions own their leases until completion/cancellation; receipts are never saved. */
public interface ProgramStructureGroupRuntime {
    /** The basic guide action uses this world displacement when its vector is unconnected. */
    default java.util.Optional<ProgramVector> defaultStructureDisplacement() { return java.util.Optional.empty(); }
    /** Completion means the force window ended, not that every body reached its target. */
    default Operation moveStructures(List<ProgramStructureReference> structures, ProgramStructureMoveRequest request) {
        return Operation.unavailable("structure_group_movement_unavailable");
    }
    default Operation rotateStructures(List<ProgramStructureReference> structures, ProgramStructurePose pose) {
        return Operation.unavailable("structure_groups_unavailable");
    }
    default Operation resetStructures(List<ProgramStructureReference> structures, ResetRequest request) {
        return Operation.unavailable("structure_groups_unavailable");
    }
    default Operation spinStructures(List<ProgramStructureReference> structures, SpinRequest request) {
        return Operation.unavailable("structure_groups_unavailable");
    }
    default Operation structureGroupStatus(UUID receipt) { return Operation.unavailable("structure_groups_unavailable"); }
    default void closeStructureGroups() {}
}
