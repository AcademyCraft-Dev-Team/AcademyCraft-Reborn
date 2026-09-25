package org.academy.internal.common.world.level.block.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnergyUpdateThrottleTest {
    @Test
    void continuousGenerationSendsAtMostFourUpdatesPerSecond() {
        var throttle = new EnergyUpdateThrottle();
        var packets = 0;
        for (var tick = 0; tick < 100; tick++) {
            for (var transfer = 0; transfer < 20; transfer++) throttle.markChanged();
            if (throttle.shouldSync(tick, tick + 1)) packets++;
        }
        assertEquals(20, packets);
    }

    @Test
    void finalMutationIsFlushedEvenWhenEnergyStopsChanging() {
        var throttle = new EnergyUpdateThrottle();
        throttle.markChanged();
        assertTrue(throttle.shouldSync(0, 100));
        throttle.markChanged();
        assertFalse(throttle.shouldSync(1, 0));
        assertFalse(throttle.shouldSync(4, 0));
        assertTrue(throttle.shouldSync(5, 0));
        assertFalse(throttle.shouldSync(10, 0));
    }

    @Test
    void generationAndExtractionReturningToSameValueNeedNoPacket() {
        var throttle = new EnergyUpdateThrottle();
        throttle.markChanged();
        assertTrue(throttle.shouldSync(0, 100));
        throttle.markChanged();
        assertFalse(throttle.shouldSync(5, 100));
        assertFalse(throttle.shouldSync(20, 100));
    }

    @Test
    void clockRollbackDoesNotPreventPendingUpdate() {
        var throttle = new EnergyUpdateThrottle();
        throttle.markChanged();
        assertTrue(throttle.shouldSync(100, 100));
        throttle.markChanged();
        assertTrue(throttle.shouldSync(0, 90));
    }
}
