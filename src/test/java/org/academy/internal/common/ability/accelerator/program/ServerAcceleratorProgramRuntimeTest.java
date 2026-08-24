package org.academy.internal.common.ability.accelerator.program;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerAcceleratorProgramRuntimeTest {
    @Test
    void kineticImpactStrengthsStayWithinTheThreeSupportedLevels() {
        assertEquals(1, ServerAcceleratorProgramRuntime.impactLevel(
                AcceleratorProgramStrength.CONTROLLED));
        assertEquals(2, ServerAcceleratorProgramRuntime.impactLevel(
                AcceleratorProgramStrength.STANDARD));
        assertEquals(3, ServerAcceleratorProgramRuntime.impactLevel(
                AcceleratorProgramStrength.MAXIMUM));
    }
}
