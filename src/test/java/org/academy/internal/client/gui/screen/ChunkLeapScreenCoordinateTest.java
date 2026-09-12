package org.academy.internal.client.gui.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChunkLeapScreenCoordinateTest {
    @Test
    void emptyAxisDefaultsToZero() {
        assertEquals(0, ChunkLeapScreen.coordinateValue(null));
        assertEquals(0, ChunkLeapScreen.coordinateValue(""));
        assertEquals(0, ChunkLeapScreen.coordinateValue("   "));
    }

    @Test
    void axesAreParsedIndependently() {
        assertEquals(128, ChunkLeapScreen.coordinateValue("128"));
        assertEquals(-384, ChunkLeapScreen.coordinateValue("-384"));
    }

    @Test
    void surroundingWhitespaceIsIgnored() {
        assertEquals(42, ChunkLeapScreen.coordinateValue(" 42 "));
    }

    @Test
    void malformedAxisIsRejected() {
        assertThrows(NumberFormatException.class, () -> ChunkLeapScreen.coordinateValue("1 2"));
    }
}
