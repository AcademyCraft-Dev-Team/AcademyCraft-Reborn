package org.academy.api.server.time;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemporalAccumulatorTest {
    @Test
    void fractionalScaleProducesDeterministicTicks() {
        var accumulator = new TemporalAccumulator();
        var total = 0;
        for (var i = 0; i < 20; i++) {
            total += accumulator.advance(0.25D, 8);
        }
        assertEquals(5, total);
        assertEquals(0.0D, accumulator.credit(), 1.0E-12D);
    }

    @Test
    void accelerationRetainsOnlyFractionalCredit() {
        var accumulator = new TemporalAccumulator();
        assertEquals(2, accumulator.advance(2.5D, 8));
        assertEquals(3, accumulator.advance(2.5D, 8));
        assertEquals(8, accumulator.advance(100.0D, 8));
        assertTrue(accumulator.credit() < 1.0D);
    }

    @Test
    void pausePreservesExistingFractionalCredit() {
        var accumulator = new TemporalAccumulator();
        assertEquals(0, accumulator.advance(0.5D, 8));
        assertEquals(0, accumulator.advance(0.0D, 8));
        assertEquals(1, accumulator.advance(0.5D, 8));
    }

    @Test
    void rejectsInvalidInputs() {
        var accumulator = new TemporalAccumulator();
        assertThrows(IllegalArgumentException.class, () -> accumulator.advance(-1.0D, 8));
        assertThrows(IllegalArgumentException.class, () -> accumulator.advance(Double.NaN, 8));
        assertThrows(IllegalArgumentException.class, () -> accumulator.advance(1.0D, 0));
    }
}
