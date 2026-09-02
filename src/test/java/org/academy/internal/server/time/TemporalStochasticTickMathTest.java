package org.academy.internal.server.time;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TemporalStochasticTickMathTest {
    @Test
    void hardPauseSuppressesTheSelectedCallback() {
        var sampled = new AtomicBoolean();

        var invocations = TemporalStochasticTickMath.invocationCount(
                0.0D,
                () -> {
                    sampled.set(true);
                    return 0.0D;
                }
        );

        assertEquals(0, invocations);
        assertFalse(sampled.get());
    }

    @Test
    void integralScaleDoesNotPerturbTheVanillaRandomSequence() {
        var sampled = new AtomicBoolean();

        var invocations = TemporalStochasticTickMath.invocationCount(
                2.0D,
                () -> {
                    sampled.set(true);
                    return 0.0D;
                }
        );

        assertEquals(2, invocations);
        assertFalse(sampled.get());

        assertEquals(1, TemporalStochasticTickMath.invocationCount(
                1.0D + Math.ulp(1.0D),
                () -> {
                    sampled.set(true);
                    return 0.0D;
                }
        ));
        assertFalse(sampled.get());
    }

    @Test
    void fractionalScaleUsesOneBernoulliSelection() {
        assertEquals(3, TemporalStochasticTickMath.invocationCount(
                2.25D,
                () -> 0.2D
        ));
        assertEquals(2, TemporalStochasticTickMath.invocationCount(
                2.25D,
                () -> 0.3D
        ));
        assertEquals(1, TemporalStochasticTickMath.invocationCount(
                0.5D,
                () -> 0.2D
        ));
        assertEquals(0, TemporalStochasticTickMath.invocationCount(
                0.5D,
                () -> 0.8D
        ));
    }

    @Test
    void invalidScaleAndSampleAreRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                TemporalStochasticTickMath.invocationCount(-1.0D, () -> 0.0D));
        assertThrows(IllegalArgumentException.class, () ->
                TemporalStochasticTickMath.invocationCount(0.5D, () -> 1.0D));
    }
}
