package org.academy.internal.common.structure.transaction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SubLevelCaptureTargetTest {
    @Test
    void admitsStableCollisionBlocksWithoutVanillaWhitelistOrFullCubeRequirement() {
        assertNull(StructureCellPayload.classifyAdmission(false, false, false, false, false, 3.0f));
    }

    @Test
    void rejectsPayloadsThatTheSharedTransactionCannotPreserve() {
        assertEquals("block_entity_not_supported",
                StructureCellPayload.classifyAdmission(true, false, false, false, false, 3.0f));
        assertNull(StructureCellPayload.classifyAdmission(false, false, true, true, false, 3.0f));
        assertNull(StructureCellPayload.classifyAdmission(true, true, true, true, true, 3.0f));
        assertEquals("fluid_not_supported",
                StructureCellPayload.classifyAdmission(false, false, false, true, false, 3.0f));
        assertEquals("scheduled_tick_pending",
                StructureCellPayload.classifyAdmission(false, false, false, false, true, 3.0f));
    }

    @Test
    void rejectsUnbreakableButAllowsDecorationInAMixedBody() {
        assertEquals("unbreakable",
                StructureCellPayload.classifyAdmission(false, false, false, false, false, -1.0f));
        assertNull(StructureCellPayload.classifyAdmission(false, false, false, false, false, 0.0f));
    }
}
