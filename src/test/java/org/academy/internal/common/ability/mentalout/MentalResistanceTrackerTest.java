package org.academy.internal.common.ability.mentalout;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MentalResistanceTrackerTest {
    @Test
    void breaksStrictlyAfterTwentySecondsAndBlocksExactlyFiveSeconds() {
        var tracker = new MentalResistanceTracker();
        var subject = UUID.randomUUID();
        for (long tick = 100; tick <= 500; tick++) {
            tracker.mark(subject, tick);
            assertTrue(tracker.tick(tick, _ -> true).isEmpty());
        }
        tracker.mark(subject, 501);
        assertEquals(java.util.List.of(subject), tracker.tick(501, _ -> true));
        assertEquals(100, tracker.remainingTicks(subject, 501));
        assertEquals(1, tracker.remainingTicks(subject, 600));
        assertEquals(0, tracker.remainingTicks(subject, 601));
    }

    @Test
    void repeatedEffectsInOneTickDoNotAccelerateOrResetExposure() {
        var tracker = new MentalResistanceTracker();
        var subject = UUID.randomUUID();
        for (long tick = 0; tick <= 401; tick++) {
            for (int effect = 0; effect < 5; effect++) tracker.mark(subject, tick);
            assertEquals(tick == 401 ? 1 : 0, tracker.tick(tick, _ -> true).size());
        }
    }

    @Test
    void aTickWithoutAnEffectRestartsTheContinuousWindow() {
        var tracker = new MentalResistanceTracker();
        var subject = UUID.randomUUID();
        for (long tick = 0; tick <= 400; tick++) {
            tracker.mark(subject, tick);
            tracker.tick(tick, _ -> true);
        }
        tracker.tick(401, _ -> true);
        tracker.mark(subject, 402);
        assertTrue(tracker.tick(402, _ -> true).isEmpty());
        assertEquals(0, tracker.remainingTicks(subject, 402));
    }

    @Test
    void blockedAttemptsCannotExtendTheBlockOrCarryExposureIntoNextWindow() {
        var tracker = new MentalResistanceTracker();
        var subject = UUID.randomUUID();
        for (long tick = 0; tick <= 902; tick++) {
            tracker.mark(subject, tick);
            assertEquals(tick == 401 || tick == 902 ? 1 : 0, tracker.tick(tick, _ -> true).size());
        }
    }

    @Test
    void losingEligibilityAndLifecycleRemovalClearExposureAndResistance() {
        var tracker = new MentalResistanceTracker();
        var subject = UUID.randomUUID();
        for (long tick = 0; tick <= 401; tick++) {
            tracker.mark(subject, tick);
            var eligible = tick != 401;
            tracker.tick(tick, _ -> eligible);
        }
        assertEquals(0, tracker.remainingTicks(subject, 401));
        for (long tick = 402; tick <= 803; tick++) {
            tracker.mark(subject, tick);
            tracker.tick(tick, _ -> true);
        }
        assertEquals(100, tracker.remainingTicks(subject, 803));
        tracker.remove(subject);
        assertEquals(0, tracker.remainingTicks(subject, 803));
        tracker.mark(subject, 804);
        assertTrue(tracker.tick(804, _ -> true).isEmpty());
    }


    @Test
    void subjectsHaveIndependentWindowsAndServerClearResetsEverything() {
        var tracker = new MentalResistanceTracker();
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        for (long tick = 0; tick <= 401; tick++) {
            tracker.mark(first, tick);
            if (tick >= 100) tracker.mark(second, tick);
            tracker.tick(tick, _ -> true);
        }
        assertEquals(100, tracker.remainingTicks(first, 401));
        assertEquals(0, tracker.remainingTicks(second, 401));
        tracker.clear();
        tracker.mark(second, 502);
        assertTrue(tracker.tick(502, _ -> true).isEmpty());
        assertEquals(0, tracker.remainingTicks(first, 401));
    }
}
