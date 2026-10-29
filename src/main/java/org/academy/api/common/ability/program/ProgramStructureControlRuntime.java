package org.academy.api.common.ability.program;

/** Read-only control validation for asynchronous single-structure program actions. */
public interface ProgramStructureControlRuntime {
    /** Release this runtime's own single-body controls on completion, failure or cancellation. */
    default void closeStructureControls() {}
    /** Stop this window's force while retaining its control handle for subsequent actions. */
    default ProgramStructureOperationResult finishStructureMovement(ProgramStructureControl control) {
        return ProgramStructureOperationResult.completed("movement_window_completed");
    }
    /** Must not acquire control, renew commands or charge resources. */
    default ProgramStructureOperationResult structureControlStatus(ProgramStructureControl control) {
        return ProgramStructureOperationResult.unavailable("structure_control_status_unavailable");
    }
}
