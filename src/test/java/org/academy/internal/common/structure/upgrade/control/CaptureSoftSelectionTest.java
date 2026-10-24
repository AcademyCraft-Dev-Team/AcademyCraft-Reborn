package org.academy.internal.common.structure.upgrade.control;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CaptureSoftSelectionTest {
    @Test void softClearanceNeverOverridesPayloadFluidOrUnbreakableRejections() {
        for (String detail : new String[]{"block_entity_not_supported", "waterlogged_not_supported",
                "fluid_not_supported", "unbreakable", "unknown"}) {
            assertFalse(CaptureSoftClearance.canClearRejected(detail));
        }
        assertTrue(CaptureSoftClearance.canClearRejected("not_in_plain_block_whitelist"));
        assertTrue(CaptureSoftClearance.canClearRejected("not_full_block"));
        assertTrue(CaptureSoftClearance.canClearRejected("scheduled_tick_pending"));
    }
}
