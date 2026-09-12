package org.academy.internal.common.entitycontrol;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HealthOffsetStateTest {
    private static double ceiling(HealthOffsetState state) {
        return state.maximum - Double.longBitsToDouble(state.encodedOffset ^ state.mask);
    }

    @Test void waitsTenSecondsThenReleasesLinearlyAndExactly() {
        var state = new HealthOffsetState(100, 70, 10);
        state.advance(210, 100);
        assertEquals(70, ceiling(state), 1e-8);
        state.advance(310, 100);
        assertEquals(85, ceiling(state), 1e-8);
        state.advance(410, 100);
        assertEquals(100, ceiling(state), 1e-8);
    }

    @Test void denseAndSparseTicksProduceTheSameResult() {
        var dense = new HealthOffsetState(100, 70, 0);
        var sparse = new HealthOffsetState(100, 70, 0);
        for (int tick = 0; tick <= 300; tick++) {
            dense.advance(tick, 100);
            dense.advance(tick, 100); // Time acceleration / two maintenance callbacks.
        }
        sparse.advance(300, 100);
        assertEquals(ceiling(sparse), ceiling(dense), 1e-8);
    }

    @Test void rehitUsesCurrentHealthAndRestartsOnlyTheHitTimer() {
        var state = new HealthOffsetState(100, 70, 0);
        state.advance(300, 100);
        state.hit(ceiling(state) - 20, 100, 300);
        state.advance(500, 100);
        assertEquals(65, ceiling(state), 1e-8);
        state.advance(600, 100);
        assertEquals(82.5, ceiling(state), 1e-8);
    }

    @Test void healingReleasesOnlyItsAmountWithoutExtendingTheDeadline() {
        var state = new HealthOffsetState(100, 70, 0);
        state.heal(10);
        assertEquals(80, ceiling(state), 1e-8);
        state.advance(300, 100);
        assertEquals(90, ceiling(state), 1e-8);
        assertEquals(0, state.lastHit);
        state.advance(400, 100);
        assertEquals(100, ceiling(state), 1e-8);
    }

    @Test void maxHealthGrowthDoesNotReleaseTheExistingCeiling() {
        var state = new HealthOffsetState(100, 70, 0);
        state.advance(100, 200);
        assertEquals(70, ceiling(state), 1e-8);
        state.advance(400, 200);
        assertEquals(200, ceiling(state), 1e-8);
    }

    @Test void deadStateCannotDecayOrHeal() {
        var state = new HealthOffsetState(100, 0, 0);
        state.dead = true;
        state.heal(100);
        state.advance(800, 200);
        assertEquals(0, ceiling(state));
    }

    @Test void encodedFieldDoesNotContainThePlainOffset() {
        var state = new HealthOffsetState(100, 70, 0);
        assertNotEquals(Double.doubleToRawLongBits(30), state.encodedOffset);
        assertEquals(70, ceiling(state));
        state.heal(Double.NaN);
        assertEquals(70, ceiling(state));
    }
}
