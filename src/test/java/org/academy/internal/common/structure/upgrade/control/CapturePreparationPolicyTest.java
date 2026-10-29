package org.academy.internal.common.structure.upgrade.control;

import org.academy.api.common.structure.StructureCaptureResult;
import org.academy.api.common.structure.upgrade.StructureOperation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CapturePreparationPolicyTest {
    @Test
    void scheduledSourceTicksWaitWithoutWeakeningOtherAdmissionGates() {
        assertTrue(CapturePreparationPolicy.retryable(StructureOperation.Status.TERRAIN_PENDING, "terrain_coverage_unknown"));
        assertTrue(CapturePreparationPolicy.retryable(StructureOperation.Status.UNSUPPORTED, "capture_source_scheduled_tick_pending"));
        for (var detail : new String[]{"capture_source_block_entity_not_supported", "capture_source_fluid_not_supported",
                "capture_source_unbreakable", "capture_source_changed_during_preview", "scheduled_tick_pending"}) {
            assertFalse(CapturePreparationPolicy.retryable(StructureOperation.Status.UNSUPPORTED, detail), detail);
        }
        assertFalse(CapturePreparationPolicy.retryable(StructureOperation.Status.DENIED, "capture_source_scheduled_tick_pending"));
        assertFalse(CapturePreparationPolicy.retryable(StructureOperation.Status.BLOCKED, "extraction_path_blocked"));
        assertFalse(CapturePreparationPolicy.retryable(StructureOperation.Status.STALE, "terrain_revision_changed"));
    }

    @Test
    void repeatedPollsDoNotAddAttemptsOrResetOriginalDeadline() {
        assertTrue(CapturePreparationPolicy.mayAttempt(41, 42));
        assertFalse(CapturePreparationPolicy.mayAttempt(42, 42));
        assertFalse(CapturePreparationPolicy.expired(42, 141));
        assertTrue(CapturePreparationPolicy.expired(42, 142));
        assertTrue(CapturePreparationPolicy.expired(42, 41));
    }

    @Test
    void preparationHasAnOperationIdentityButDoesNotInventAStructure() {
        var id = UUID.randomUUID();
        var result = StructureCaptureResult.preparing(id, "extraction_preparing:capture_source_scheduled_tick_pending");
        assertTrue(result.pending());
        assertFalse(result.succeeded());
        assertEquals(id, result.transactionId());
        assertTrue(result.structure().isEmpty());
        assertEquals(0, result.affectedBlocks());
    }
}
