package org.academy.internal.common.structure.sable;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SableCaptureAdmissionTest {
    @Test void admitsOnlySupportedStateOnlyTerrainThroughContentAndShapeGates() {
        assertNull(SableCaptureAdmission.allowTerrain("not_in_plain_block_whitelist", true, 0));
        assertNull(SableCaptureAdmission.allowTerrain("not_full_block", true, .6f));
        assertNull(SableCaptureAdmission.allowTerrain(null, false, 1));
        assertEquals("not_full_block", SableCaptureAdmission.allowTerrain("not_full_block", false, 1));
        assertEquals("not_in_plain_block_whitelist",
                SableCaptureAdmission.allowTerrain("not_in_plain_block_whitelist", false, 1));
    }

    @Test void terrainDoesNotBypassPayloadFluidTickOrHardnessRestrictions() {
        for (String reason : new String[]{"block_entity_not_supported", "waterlogged_not_supported",
                "fluid_not_supported", "scheduled_tick_pending", "unbreakable"}) {
            assertEquals(reason, SableCaptureAdmission.allowTerrain(reason, true, 0));
        }
        assertEquals("unbreakable", SableCaptureAdmission.allowTerrain("not_in_plain_block_whitelist", true, -1));
        assertEquals("unbreakable", SableCaptureAdmission.allowTerrain("not_full_block", true, -1));
    }

    @Test void allocationNeedsARealFinitePositiveMassCollisionSeed() {
        assertTrue(SableCaptureAdmission.seedable(true, 1));
        assertFalse(SableCaptureAdmission.seedable(false, 1));
        for (double mass : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertFalse(SableCaptureAdmission.seedable(true, mass));
        }
    }
}
