package org.academy.internal.common.ability.mentalout.precision;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrecisionOperationPolicyTest {
    @Test
    void actionSubjectLimitScalesToLargeRosters() {
        assertEquals(16, PrecisionOperationRuntime.actionSubjectLimit(0));
        assertEquals(32, PrecisionOperationRuntime.actionSubjectLimit(1));
        assertEquals(64, PrecisionOperationRuntime.actionSubjectLimit(2));
        assertEquals(16, PrecisionOperationRuntime.actionSubjectLimit(-1));
        assertEquals(64, PrecisionOperationRuntime.actionSubjectLimit(3));
    }

    @Test
    void retiredFirstMilestoneCostReductionIsAlwaysAvailable() {
        assertEquals(9.0f, PrecisionOperationRuntime.unlockedDynamicCost(10.0f));
        assertEquals(0.0f, PrecisionOperationRuntime.unlockedDynamicCost(0.0f));
    }
}
