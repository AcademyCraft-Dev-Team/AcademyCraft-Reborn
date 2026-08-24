package org.academy.internal.common.skilldata;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OutputControlDataTest {
    @Test
    void abilityOutputUsesTheSelectablePointZeroOneToTwoRange() {
        var data = new OutputControlData();

        data.setAbilityOutput(0.0f);
        assertEquals(0.01f, data.getAbilityOutput(), 1.0E-6f);
        data.setAbilityOutput(3.0f);
        assertEquals(2.0f, data.getAbilityOutput(), 1.0E-6f);
        data.setAbilityOutput(Float.NaN);
        assertEquals(1.0f, data.getAbilityOutput(), 1.0E-6f);
    }
}
