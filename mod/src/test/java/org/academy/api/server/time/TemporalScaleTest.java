package org.academy.api.server.time;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TemporalScaleTest {
    @Test
    void composesAndClampsScales() {
        assertEquals(0.75D, TemporalScale.compose(List.of(0.5D, 1.5D), false, 8.0D), 1.0E-12D);
        assertEquals(8.0D, TemporalScale.compose(List.of(4.0D, 4.0D), false, 8.0D));
        assertEquals(8.0D, TemporalScale.compose(List.of(4.0D, 4.0D, 0.5D), false, 8.0D));
    }

    @Test
    void hardPauseDominatesUnlessImmune() {
        var scales = List.of(2.0D, 0.0D, 0.5D);
        assertEquals(0.0D, TemporalScale.compose(scales, false, 8.0D));
        assertEquals(1.0D, TemporalScale.compose(scales, true, 8.0D), 1.0E-12D);
    }

    @Test
    void rejectsInvalidScales() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TemporalScale.compose(List.of(-0.1D), false, 8.0D)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> TemporalScale.compose(List.of(1.0D), false, 0.5D)
        );
    }
}
