package org.academy.internal.common.structure.transaction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SubLevelCaptureTargetTest {
    @Test
    void admitsStableCollisionBlocksWithoutVanillaWhitelistOrFullCubeRequirement() {
        assertNull(SubLevelCaptureTarget.classifyAdmission(false, false, false, false, false, 3.0f));
    }

    @Test
    void rejectsPayloadsThatTheSharedTransactionCannotPreserve() {
        assertEquals("block_entity_not_supported",
                SubLevelCaptureTarget.classifyAdmission(true, false, false, false, false, 3.0f));
        assertEquals("waterlogged_not_supported",
                SubLevelCaptureTarget.classifyAdmission(false, true, false, false, false, 3.0f));
        assertEquals("fluid_not_supported",
                SubLevelCaptureTarget.classifyAdmission(false, false, true, false, false, 3.0f));
        assertEquals("scheduled_tick_pending",
                SubLevelCaptureTarget.classifyAdmission(false, false, false, true, false, 3.0f));
    }

    @Test
    void rejectsUnbreakableOrEmptyGeometryInsteadOfLosingTheSource() {
        assertEquals("unbreakable",
                SubLevelCaptureTarget.classifyAdmission(false, false, false, false, false, -1.0f));
        assertEquals("no_collision_geometry",
                SubLevelCaptureTarget.classifyAdmission(false, false, false, false, true, 0.0f));
    }
}
