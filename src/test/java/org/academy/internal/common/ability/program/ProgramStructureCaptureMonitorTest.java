package org.academy.internal.common.ability.program;

import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.ProgramStructureOperationResult;
import org.academy.api.common.structure.StructureCaptureResult;
import org.academy.api.common.structure.StructureRef;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProgramStructureCaptureMonitorTest {
    @Test
    void delayedCaptureFailureSurvivesTheSkillsSessionRemovalAndKeepsItsTransactionIdentity() {
        var transaction = UUID.randomUUID();
        var reference = reference();
        assertNotEquals(transaction, reference.id());
        var monitor = new ProgramStructureCaptureMonitor();
        monitor.track(Optional.of(transaction));
        assertTrue(monitor.pollFailure(id -> {
            assertEquals(transaction, id);
            return Optional.of(StructureCaptureResult.pending(id, reference, "await_activation"));
        }).isEmpty());

        // The skill has removed its temporary session; the transaction still owns the result.
        var failure = monitor.pollFailure(id -> {
            assertEquals(transaction, id);
            return Optional.of(StructureCaptureResult.failure(id,
                    StructureCaptureResult.Status.TARGET_WRITE_FAILED, "target_finalize_failed"));
        }).orElseThrow();
        assertEquals(ProgramStructureOperationResult.Status.REJECTED, failure.status());
        assertEquals("TARGET_WRITE_FAILED:target_finalize_failed", failure.detail());
        assertEquals(ProgramVmDiagnostic.TARGET_PHYSICS_FINALIZE_FAILED,
                ProgramVmDiagnostic.structureFailure(failure));
        assertTrue(monitor.pollFailure(_ -> fail("terminal capture must not be polled twice")).isEmpty());
    }

    @Test
    void successfulCaptureDoesNotBecomeAFailureWhenItsFinishedResultIsLaterEvicted() {
        var transaction = UUID.randomUUID();
        var monitor = new ProgramStructureCaptureMonitor();
        monitor.track(Optional.of(transaction));
        assertTrue(monitor.pollFailure(id -> Optional.of(
                StructureCaptureResult.success(id, reference(), 1))).isEmpty());
        assertTrue(monitor.pollFailure(_ -> fail("successful capture must stop polling")).isEmpty());
    }

    @Test
    void replacingOrClearingAnExtractionDoesNotReuseItsOldTransaction() {
        var monitor = new ProgramStructureCaptureMonitor();
        monitor.track(Optional.of(UUID.randomUUID()));
        var replacement = UUID.randomUUID();
        monitor.track(Optional.of(replacement));
        var failure = monitor.pollFailure(id -> {
            assertEquals(replacement, id);
            return Optional.empty();
        }).orElseThrow();
        assertEquals(ProgramStructureOperationResult.Status.UNAVAILABLE, failure.status());
        assertEquals("capture_transaction_unavailable", failure.detail());
        monitor.track(Optional.of(UUID.randomUUID()));
        monitor.track(Optional.empty());
        assertTrue(monitor.pollFailure(_ -> fail("synchronous extraction has no pending transaction")).isEmpty());
    }

    private static StructureRef reference() {
        return new StructureRef(UUID.randomUUID(), Identifier.parse("minecraft:overworld"),
                Identifier.parse("sable:structure"), 1);
    }
}
