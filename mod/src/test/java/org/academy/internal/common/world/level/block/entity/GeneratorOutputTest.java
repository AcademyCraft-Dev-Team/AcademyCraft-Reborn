package org.academy.internal.common.world.level.block.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeneratorOutputTest {
    @Test
    void solarGeneratorProducesFixedEnergyWhileSunlit() {
        assertEquals(0, GeneratorOutput.solar(0));
        assertEquals(512, GeneratorOutput.solar(1));
        assertEquals(512, GeneratorOutput.solar(15));
    }

    @Test
    void windGeneratorProducesConfiguredEnergyPerTick() {
        assertEquals(4_096, GeneratorOutput.WIND_PER_TICK);
    }
}
