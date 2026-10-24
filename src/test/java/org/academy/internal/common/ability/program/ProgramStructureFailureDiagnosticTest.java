package org.academy.internal.common.ability.program;

import org.academy.api.common.ability.program.ProgramStructureOperationResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProgramStructureFailureDiagnosticTest {
    @Test
    void captureStatusPrefixesPreserveSpecificUserFacingReasons() {
        assertReason("BACKEND_UNAVAILABLE:sublevel_runtime_unavailable",
                ProgramVmDiagnostic.SUBLEVEL_RUNTIME_UNAVAILABLE);
        assertReason("BACKEND_UNAVAILABLE:sublevel_plot_capacity_unavailable",
                ProgramVmDiagnostic.SUBLEVEL_PLOT_CAPACITY_UNAVAILABLE);
        assertReason("TARGET_WRITE_FAILED:target_physics_prepare_failed",
                ProgramVmDiagnostic.TARGET_PHYSICS_PREPARE_FAILED);
        assertReason("TARGET_WRITE_FAILED:target_physics_finalize_failed",
                ProgramVmDiagnostic.TARGET_PHYSICS_FINALIZE_FAILED);
        assertEquals(ProgramVmDiagnostic.TARGET_PHYSICS_FINALIZE_FAILED, ProgramVmDiagnostic.structureFailure(
                ProgramStructureOperationResult.rejected("TARGET_WRITE_FAILED:target_finalize_failed")));
        assertReason("BACKEND_UNAVAILABLE:no_free_native_slot",
                ProgramVmDiagnostic.NO_FREE_NATIVE_SLOT);
        assertReason("UNSUPPORTED_BLOCK:not_in_plain_block_whitelist",
                ProgramVmDiagnostic.NOT_IN_PLAIN_BLOCK_WHITELIST);
    }

    @Test
    void unknownDetailsRetainStatusAndDoNotMatchPartialReasonTokens() {
        assertEquals(ProgramVmDiagnostic.ACTION_UNAVAILABLE, ProgramVmDiagnostic.structureFailure(
                ProgramStructureOperationResult.unavailable("unknown_backend_condition")));
        assertEquals(ProgramVmDiagnostic.ACTION_REJECTED, ProgramVmDiagnostic.structureFailure(
                ProgramStructureOperationResult.rejected("previous_no_free_native_slot_is_resolved")));
    }

    private static void assertReason(String detail, ProgramVmDiagnostic expected) {
        assertEquals(expected, ProgramVmDiagnostic.structureFailure(
                ProgramStructureOperationResult.rejected(detail)));
        assertEquals("message.academy.program.execution.diagnostic."
                + detail.substring(detail.indexOf(':') + 1), expected.translationKey());
    }
}
