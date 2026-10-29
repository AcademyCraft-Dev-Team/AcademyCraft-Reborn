package org.academy.internal.common.structure.upgrade.control;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExtractionHoverClockTest {
    @Test void ascentDoesNotConsumeTheFullTwoHundredTickHold() {
        var clock = new ExtractionHoverClock(200);
        for (int tick = 0; tick < 1_000; tick++) clock.observe(tick, false);
        assertEquals(0, clock.elapsedTicks());
        for (int tick = 1_000; tick < 1_200; tick++) clock.observe(tick, true);
        assertEquals(199, clock.elapsedTicks());
        assertEquals(200, clock.observe(1_200, true));
        assertEquals(200, clock.observe(1_201, true));
    }

    @Test void repeatedPollsAndFourSolverSubstepsCannotMultiplyTime() {
        var clock = new ExtractionHoverClock(200);
        for (int tick = 0; tick <= 200; tick++) {
            for (int substep = 0; substep < 4; substep++) {
                assertEquals(tick, clock.observe(tick, true));
                assertEquals(tick, clock.elapsedTicks());
            }
        }
    }

    @Test void terrainWaitAndLeavingTheTargetPreserveOnlyActuallyCompletedHoverTicks() {
        var clock = new ExtractionHoverClock(200);
        for (int tick = 0; tick < 50; tick++) clock.observe(tick, true);
        for (int tick = 50; tick < 150; tick++) clock.observe(tick, false);
        assertEquals(50, clock.elapsedTicks());
        for (int tick = 150; tick <= 300; tick++) clock.observe(tick, true);
        assertEquals(200, clock.elapsedTicks());
    }

    @Test void oneFailedSubstepInvalidatesItsWholeTickAndCannotBeRevivedByAnotherPoll() {
        var clock = new ExtractionHoverClock(200);
        clock.observe(10, true);
        clock.observe(10, false);
        clock.observe(10, true);
        assertEquals(0, clock.observe(11, true));
        assertEquals(1, clock.observe(12, true));
    }

    @Test void clockJumpsAreNotReplayedAsElapsedPhysics() {
        var clock = new ExtractionHoverClock(200);
        clock.observe(10, true);
        assertEquals(0, clock.observe(1_000, true));
        assertEquals(1, clock.observe(1_001, true));
        assertEquals(1, clock.observe(1, true));
        assertEquals(2, clock.observe(2, true));
    }

    @Test void wrappingServerTickCounterStillCountsOneRealTick() {
        var clock = new ExtractionHoverClock(200);
        clock.observe(Integer.MAX_VALUE, true);
        assertEquals(1, clock.observe(Integer.MIN_VALUE, true));
        assertThrows(IllegalArgumentException.class, () -> new ExtractionHoverClock(-1));
    }
}
