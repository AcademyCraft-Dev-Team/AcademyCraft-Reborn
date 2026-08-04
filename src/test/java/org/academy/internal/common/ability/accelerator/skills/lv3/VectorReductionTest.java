package org.academy.internal.common.ability.accelerator.skills.lv3;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VectorReductionTest {
    @Test
    void levelScalingStaysWithinTheConfiguredField() {
        assertEquals(6.0f, VectorReduction.getRadius(1), 0.0001f);
        assertEquals(10.0f, VectorReduction.getRadius(3), 0.0001f);
        assertEquals(0.5, VectorReduction.getSlowdownPercent(1), 0.0001);
        assertEquals(0.8, VectorReduction.getSlowdownPercent(2), 0.0001);
    }
}
