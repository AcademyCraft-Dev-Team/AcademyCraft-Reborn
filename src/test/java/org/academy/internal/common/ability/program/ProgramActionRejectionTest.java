package org.academy.internal.common.ability.program;

import org.academy.api.common.ability.program.ProgramActionRejection;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProgramActionRejectionTest {
    private static final Map<ProgramActionRejection.Reason, ProgramVmDiagnostic> DIAGNOSTICS = Map.of(
            ProgramActionRejection.Reason.PREPARING, ProgramVmDiagnostic.ACTION_PREPARING,
            ProgramActionRejection.Reason.STALE_BINDING, ProgramVmDiagnostic.ACTION_BINDING_STALE,
            ProgramActionRejection.Reason.CONFLICTING_INPUTS, ProgramVmDiagnostic.ACTION_INPUT_CONFLICT,
            ProgramActionRejection.Reason.INVALID_INPUT, ProgramVmDiagnostic.ACTION_INPUT_INVALID,
            ProgramActionRejection.Reason.TARGET_UNREACHABLE, ProgramVmDiagnostic.ACTION_TARGET_UNREACHABLE,
            ProgramActionRejection.Reason.ACTION_BUSY, ProgramVmDiagnostic.ACTION_BUSY,
            ProgramActionRejection.Reason.NATIVE_CAST_REJECTED, ProgramVmDiagnostic.NATIVE_CAST_REJECTED,
            ProgramActionRejection.Reason.UNAVAILABLE, ProgramVmDiagnostic.ACTION_UNAVAILABLE);

    @Test
    void wrappedTypedReasonsTakePrecedenceOverIncidentalExceptionMessages() {
        assertEquals(ProgramActionRejection.Reason.values().length, DIAGNOSTICS.size());
        DIAGNOSTICS.forEach((reason, expected) -> {
            var rejection = new ProgramActionRejection(reason, new IllegalArgumentException("zero"));
            var wrapped = new IllegalStateException("Target is invalid", rejection);
            assertEquals(expected, AbilityProgramManager.actionDiagnostic(wrapped), reason.name());
            assertEquals(reason, rejection.reason());
        });
    }

    @Test
    void sequentialAndDeferredValidationReportTheSameReasonAndNode() {
        DIAGNOSTICS.forEach((reason, expected) -> {
            var sequential = ProgramActionTransaction.sequential();
            var immediateFailure = assertThrows(ProgramActionTransaction.SettlementException.class,
                    () -> sequential.stage(17, rejectingAction(reason)));
            var deferred = new ProgramActionTransaction();
            deferred.stage(17, rejectingAction(reason));
            var deferredFailure = deferred.commit();

            assertFalse(deferredFailure.successful());
            assertEquals(17, immediateFailure.result().nodeId());
            assertEquals(17, deferredFailure.nodeId());
            assertEquals(expected, AbilityProgramManager.actionDiagnostic(immediateFailure));
            assertEquals(expected, AbilityProgramManager.actionDiagnostic(sequential.abort().cause()));
            assertEquals(expected, AbilityProgramManager.actionDiagnostic(deferredFailure.cause()));
        });
    }

    @Test
    void unrecognizedCyclicCauseChainsRemainGeneric() {
        var first = new IllegalStateException("unknown first");
        var second = new IllegalStateException("unknown second", first);
        first.initCause(second);
        assertEquals(ProgramVmDiagnostic.ACTION_REJECTED, AbilityProgramManager.actionDiagnostic(first));
    }

    private static ProgramActionTransaction.ProgramAction rejectingAction(ProgramActionRejection.Reason reason) {
        return new ProgramActionTransaction.ProgramAction() {
            @Override
            public void validate() {
                throw new IllegalStateException("Extension bridge failed", new ProgramActionRejection(reason));
            }

            @Override
            public ProgramActionTransaction.Undo apply() {
                fail("Rejected validation must never apply an action");
                return ProgramActionTransaction.Undo.NONE;
            }
        };
    }
}
