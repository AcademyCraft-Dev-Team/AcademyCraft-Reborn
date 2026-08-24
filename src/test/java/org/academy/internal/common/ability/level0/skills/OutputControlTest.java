package org.academy.internal.common.ability.level0.skills;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutputControlTest {
    @Test
    void cpMultiplierUsesConfiguredMarginalCurve() {
        assertEquals(0.1f, OutputControl.cpMultiplier(0.0f), 1.0E-6f);
        assertEquals(0.1f, OutputControl.cpMultiplier(0.01f), 1.0E-6f);
        assertEquals(1.0f, OutputControl.cpMultiplier(1.0f), 1.0E-6f);
        assertEquals(4.0f, OutputControl.cpMultiplier(2.0f), 1.0E-6f);
    }

    @Test
    void damageUsesTheMarginalEffectCurveAndClampsEndpoints() {
        assertEquals(0.2f, OutputControl.scaleDamage(20.0f, 0.0f), 1.0E-6f);
        assertEquals(20.0f, OutputControl.scaleDamage(20.0f, 1.0f), 1.0E-6f);
        assertEquals(40.0f, OutputControl.scaleDamage(20.0f, 3.0f), 1.0E-6f);
        assertEquals(0.5f, OutputControl.scaleDamage(0.5f, 1.0f), 1.0E-6f);
    }

    @Test
    void finalizedHealthLossIsNotScaledTwice() {
        assertEquals(4.0f, OutputControl.scaleHealthLoss(4.0f, 0.0f, true), 1.0E-6f);
        assertEquals(0.4f, OutputControl.scaleHealthLoss(0.4f, 0.0f, true), 1.0E-6f);
        assertEquals(4.0f, OutputControl.scaleHealthLoss(4.0f, 1.0f, true), 1.0E-6f);
    }

    @Test
    void precisionOperationBypassIsNestedAndScoped() {
        assertFalse(OutputControl.isOutputAdjustmentBypassed());
        var nested = OutputControl.callWithoutOutputAdjustment(() -> {
            assertTrue(OutputControl.isOutputAdjustmentBypassed());
            return OutputControl.callWithoutOutputAdjustment(
                    OutputControl::isOutputAdjustmentBypassed);
        });

        assertTrue(nested);
        assertFalse(OutputControl.isOutputAdjustmentBypassed());
    }
}
