package org.academy.internal.common.structure.space;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NativeStructureMiningProgressTest {
    @Test
    void heartbeatAndEarlyStopCannotCompleteAnUnworkedBlock() {
        var progress = new NativeStructureMiningProgress(20);
        progress.stop();
        for (int index = 0; index < 100; index++) progress.hold(20);
        assertFalse(progress.shouldFinish());
        progress.advance(20, true, 0.4);
        assertFalse(progress.advance(20, true, 1));
        assertFalse(progress.shouldFinish());
        progress.advance(21, true, 0.6);
        assertTrue(progress.shouldFinish());
        assertEquals(9, progress.stage());
    }

    @Test
    void temporalPauseCannotBeRecreditedInTheSameServerTick() {
        var progress = new NativeStructureMiningProgress(10);
        progress.advance(10, false, 1);
        assertFalse(progress.advance(10, true, 1));
        assertFalse(progress.ready());
        progress.advance(11, true, 0.5);
        progress.advance(12, false, 1);
        progress.advance(13, true, 0.25);
        assertEquals(7, progress.stage());
        assertFalse(progress.ready());
    }

    @Test
    void progressSumsCurrentSpeedWithoutRetroactiveSpeedMultiplication() {
        var progress = new NativeStructureMiningProgress(0);
        for (int tick = 0; tick < 10; tick++) progress.advance(tick, true, 0.01);
        progress.advance(10, true, 0.5);
        assertEquals(6, progress.stage());
        assertFalse(progress.ready());
        progress.advance(11, true, 0.4);
        assertTrue(progress.ready());
        assertFalse(progress.shouldFinish());
    }

    @Test
    void disconnectedOrSilentClientsLoseTheLeaseAndOlderHoldsCannotReviveIt() {
        var progress = new NativeStructureMiningProgress(100);
        assertFalse(progress.expired(140));
        assertTrue(progress.expired(141));
        progress.hold(90);
        assertTrue(progress.expired(141));
        assertTrue(progress.expired(99));
    }

    @Test
    void invalidSpeedsAndUnbreakableStatesGrantNoProgress() {
        assertEquals(0, NativeStructureMiningProgress.increment(-1, 100, true));
        assertEquals(0, NativeStructureMiningProgress.increment(1, -1, true));
        assertEquals(0, NativeStructureMiningProgress.increment(1, Float.NaN, true));
        assertEquals(0, NativeStructureMiningProgress.increment(1, Float.POSITIVE_INFINITY, true));
        assertEquals(1, NativeStructureMiningProgress.increment(0, 1, false));
        assertEquals(0.1, NativeStructureMiningProgress.increment(2, 6, true), 1.0e-10);
        assertEquals(0.03, NativeStructureMiningProgress.increment(2, 6, false), 1.0e-10);
    }
}
