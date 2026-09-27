package org.academy.internal.client.ability.teleport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The transition-attribution window decides whether a dimension change is shown the chunk-leap loading
 * screen or the vanilla one. Getting it wrong either hides the vanilla screen for unrelated portal
 * trips, or fails to hide it for our own jump.
 */
class ChunkLeapTransitionWindowTest {
    private static final long WINDOW = 15_000L;

    @Test
    void neverArmedMeansNoAttribution() {
        assertFalse(ChunkLeapTransition.withinWindow(Long.MIN_VALUE, 1_000L, WINDOW));
    }

    @Test
    void justArmedIsAttributed() {
        assertTrue(ChunkLeapTransition.withinWindow(1_000L, 1_000L, WINDOW));
    }

    @Test
    void insideTheWindowIsAttributed() {
        assertTrue(ChunkLeapTransition.withinWindow(1_000L, 1_000L + WINDOW, WINDOW));
    }

    @Test
    void pastTheWindowIsNotAttributed() {
        assertFalse(ChunkLeapTransition.withinWindow(1_000L, 1_000L + WINDOW + 1, WINDOW));
    }

    @Test
    void aClockThatWentBackwardsStillAttributes() {
        // System time can jump; a negative elapsed must not be read as "expired".
        assertTrue(ChunkLeapTransition.withinWindow(5_000L, 1_000L, WINDOW));
    }
}
