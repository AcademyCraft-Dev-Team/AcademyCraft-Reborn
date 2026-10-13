package org.academy.api.common.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StructureCaptureResultTest {
    @Test
    void keepsExplicitRejectionsAndRecoveryApartFromSuccess() {
        var transactionId = UUID.randomUUID();
        var reference = new StructureRef(UUID.randomUUID(),
                Identifier.fromNamespaceAndPath("minecraft", "overworld"),
                Identifier.fromNamespaceAndPath("academy", "native_level"), 0);
        var pending = StructureCaptureResult.pending(transactionId, reference, "awaiting_target_activation");
        assertTrue(pending.pending());
        assertFalse(pending.succeeded());
        assertEquals(reference, pending.structure().orElseThrow());
        assertEquals("awaiting_target_activation", pending.detail());

        var success = StructureCaptureResult.success(transactionId, reference, 7);
        assertTrue(success.succeeded());
        assertEquals(7, success.affectedBlocks());

        var rejected = StructureCaptureResult.failure(transactionId, StructureCaptureResult.Status.REJECTED,
                new BlockPos(1, 2, 3), "block_entity_not_supported");
        assertEquals(StructureCaptureResult.Status.REJECTED, rejected.status());
        assertEquals(new BlockPos(1, 2, 3), rejected.problemPosition().orElseThrow());
        assertTrue(rejected.structure().isEmpty());
        assertEquals("block_entity_not_supported", rejected.detail());

        var recovery = StructureCaptureResult.failure(transactionId,
                StructureCaptureResult.Status.RECOVERY_REQUIRED, "source_rollback_blocked");
        assertEquals(StructureCaptureResult.Status.RECOVERY_REQUIRED, recovery.status());
    }

    @Test
    void failureFactoriesCannotMasqueradeAsLiveOrSuccessfulTransactions() {
        var transactionId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> StructureCaptureResult.failure(transactionId,
                StructureCaptureResult.Status.SUCCESS, "success_is_not_a_failure"));
        assertThrows(IllegalArgumentException.class, () -> StructureCaptureResult.failure(transactionId,
                StructureCaptureResult.Status.PENDING, "pending_is_not_a_failure"));
    }
}
