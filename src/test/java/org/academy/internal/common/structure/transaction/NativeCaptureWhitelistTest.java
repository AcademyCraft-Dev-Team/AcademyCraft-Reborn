package org.academy.internal.common.structure.transaction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NativeCaptureWhitelistTest {
    @Test
    void curatedFullBlockIsAdmittedWithoutAnySemanticRandomTickGate() {
        // Randomly ticking surface land is admitted by the curated set, not by a semantic gate;
        // the real grass-block admission is exercised by the transaction game test.
        assertNull(NativeCaptureWhitelist.classifyAdmission(false, false, false, false,
                true, true, false, 0.6f));
    }

    @Test
    void everyStructuralGateStillRejectsWithItsOwnReason() {
        assertEquals("block_entity_not_supported", NativeCaptureWhitelist.classifyAdmission(
                true, false, false, false, true, true, false, 1.5f));
        assertEquals("waterlogged_not_supported", NativeCaptureWhitelist.classifyAdmission(
                false, true, false, false, true, true, false, 1.5f));
        assertEquals("fluid_not_supported", NativeCaptureWhitelist.classifyAdmission(
                false, false, true, false, true, true, false, 1.5f));
        assertEquals("scheduled_tick_pending", NativeCaptureWhitelist.classifyAdmission(
                false, false, false, true, true, true, false, 1.5f));
        assertEquals("not_in_plain_block_whitelist", NativeCaptureWhitelist.classifyAdmission(
                false, false, false, false, false, false, false, 0.6f));
        assertEquals("not_full_block", NativeCaptureWhitelist.classifyAdmission(
                false, false, false, false, true, false, false, 0.6f));
        assertEquals("unbreakable", NativeCaptureWhitelist.classifyAdmission(
                false, false, false, false, true, true, false, -1.0f));
        assertEquals("gravity_block_not_supported", NativeCaptureWhitelist.classifyAdmission(
                false, false, false, false, true, true, true, 0.6f));
    }

    @Test
    void scheduledTickGatePrecedesSetMembershipAndFullShape() {
        assertEquals("scheduled_tick_pending", NativeCaptureWhitelist.classifyAdmission(
                false, false, false, true, false, false, false, -1.0f));
    }
}
