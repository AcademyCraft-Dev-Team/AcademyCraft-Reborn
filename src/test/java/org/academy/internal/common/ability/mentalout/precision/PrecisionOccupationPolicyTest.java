package org.academy.internal.common.ability.mentalout.precision;

import org.academy.internal.server.config.AbilityConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrecisionOccupationPolicyTest {
    @Test
    void finiteDurationUsesHalfTheSecondsClampedToFiveAndTwentyIterations() {
        var now = 1_000L;

        assertEquals(5, PrecisionOperationRuntime.durationIterationPoints(now, now + 20L));
        assertEquals(5, PrecisionOperationRuntime.durationIterationPoints(now, now + 200L));
        assertEquals(6, PrecisionOperationRuntime.durationIterationPoints(now, now + 220L));
        assertEquals(20, PrecisionOperationRuntime.durationIterationPoints(now, now + 800L));
        assertEquals(20, PrecisionOperationRuntime.durationIterationPoints(now, now + 2_400L));
        assertEquals(0, PrecisionOperationRuntime.durationIterationPoints(now, Long.MAX_VALUE));
    }

    @Test
    void defaultPrecisionNodeCostsMatchTheBalanceTable() {
        var settings = new AbilityConfig.MentaloutSettings();

        assertEquals(10.0f, settings.commandPositioningCostPerTarget);
        assertEquals(5.0f, settings.precisionPathCostPerTarget);
        assertEquals(5.0f, settings.precisionViewCostPerTarget);
        assertEquals(10.0f, settings.precisionGuardCostPerTarget);
        assertEquals(20.0f, settings.precisionMisidentificationCostPerTarget);
        assertEquals(10.0f, settings.precisionStuporCostPerTarget);
        assertEquals(10.0f, settings.precisionImpressionCostPerTarget);
        assertEquals(20.0f, settings.precisionSensoryCostLevel0);
        assertEquals(15.0f, settings.precisionSensoryCostLevel1);
        assertEquals(10.0f, settings.precisionSensoryCostLevel2);
        assertEquals(20.0f, settings.precisionIntrusionCostLevel0);
        assertEquals(15.0f, settings.precisionIntrusionCostLevel1);
        assertEquals(10.0f, settings.precisionIntrusionCostLevel2);
    }
}
