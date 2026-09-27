package org.academy.internal.common.world.level.block.entity;

final class GeneratorOutput {
    static final int SOLAR_PER_TICK = 512;
    static final int WIND_PER_TICK = 4_096;

    private GeneratorOutput() {
    }

    static int solar(int brightness) {
        return brightness > 0 ? SOLAR_PER_TICK : 0;
    }
}
