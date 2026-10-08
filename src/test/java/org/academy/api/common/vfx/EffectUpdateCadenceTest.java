package org.academy.api.common.vfx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EffectUpdateCadenceTest {
    @Test void samplesAreTickBasedRatherThanFrameBased() {
        var cadence = new EffectUpdateCadence(); var world = new Object();
        int samples = 0;
        for (int tick = 0; tick < 20; tick++) for (int frame = 0; frame < 120; frame++)
            if (cadence.due(world, tick, 9, 4, 0)) samples++;
        assertEquals(5, samples);
    }

    @Test void movementScopeClockRollbackAndInvalidationRefreshImmediately() {
        var cadence = new EffectUpdateCadence(); var world = new Object();
        assertTrue(cadence.due(world, 4, 9, 4, 0));
        assertFalse(cadence.due(world, 7, 9, 4, 0));
        assertTrue(cadence.due(world, 6, 9, 4, 0));
        assertTrue(cadence.due(world, 6, 10, 4, 0));
        assertFalse(cadence.due(world, 6, 10, 4, 0));
        cadence.invalidate();
        assertTrue(cadence.due(world, 6, 10, 4, 0));
        assertTrue(cadence.due(new Object(), 6, 10, 4, 0));
    }

    @Test void staggerSpreadsPeriodicWorkAcrossTicks() {
        var cadence = new EffectUpdateCadence(); var world = new Object();
        assertTrue(cadence.due(world, 0, 9, 4, 2));
        assertFalse(cadence.due(world, 1, 9, 4, 2));
        assertTrue(cadence.due(world, 2, 9, 4, 2));
        assertFalse(cadence.due(world, 5, 9, 4, 2));
        assertTrue(cadence.due(world, 6, 9, 4, 2));
    }
}
