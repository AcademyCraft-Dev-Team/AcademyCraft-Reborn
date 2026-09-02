package org.academy.internal.server.time;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TemporalScheduledTickMathTest {
    @Test
    void relativeScaleAvoidsDoubleApplyingTheLevelClock() {
        assertEquals(1.0D, TemporalScheduledTickMath.relativeScale(2.0D, 2.0D));
        assertEquals(2.0D, TemporalScheduledTickMath.relativeScale(2.0D, 1.0D));
        assertEquals(0.5D, TemporalScheduledTickMath.relativeScale(1.0D, 2.0D));
        assertEquals(0.0D, TemporalScheduledTickMath.relativeScale(0.0D, 0.0D));
        assertEquals(1.0D, TemporalScheduledTickMath.relativeScale(1.0D, 0.0D));
    }

    @Test
    void rebasePreservesTemporalRemainingDelay() {
        var temporalRemaining = TemporalScheduledTickMath.temporalRemaining(
                100L,
                200L,
                1.0D,
                null
        );
        assertEquals(100.0D, temporalRemaining);
        assertEquals(150L, TemporalScheduledTickMath.rebasedTrigger(
                100L,
                temporalRemaining,
                2.0D
        ));

        var acceleratedRemaining = TemporalScheduledTickMath.temporalRemaining(
                100L,
                150L,
                2.0D,
                null
        );
        assertEquals(300L, TemporalScheduledTickMath.rebasedTrigger(
                100L,
                acceleratedRemaining,
                0.5D
        ));
    }

    @Test
    void pauseStoresDelayAndResumeRestoresItFromTheNewNow() {
        assertEquals(101L, TemporalScheduledTickMath.rebasedTrigger(
                100L,
                80.0D,
                0.0D
        ));
        assertEquals(1080L, TemporalScheduledTickMath.rebasedTrigger(
                1000L,
                TemporalScheduledTickMath.temporalRemaining(
                        1000L,
                        1001L,
                        0.0D,
                        80.0D
                ),
                1.0D
        ));
    }

    @Test
    void newDelayIsShortenedOrExtendedWithoutBecomingZero() {
        assertEquals(5, TemporalScheduledTickMath.scaleNewDelay(10, 2.0D));
        assertEquals(20, TemporalScheduledTickMath.scaleNewDelay(10, 0.5D));
        assertEquals(1, TemporalScheduledTickMath.scaleNewDelay(10, 0.0D));
        assertEquals(0, TemporalScheduledTickMath.scaleNewDelay(0, 2.0D));
    }

    @Test
    void invalidScaleIsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                TemporalScheduledTickMath.relativeScale(-1.0D, 1.0D));
        assertThrows(IllegalArgumentException.class, () ->
                TemporalScheduledTickMath.scaleNewDelay(10, Double.NaN));
    }
}
