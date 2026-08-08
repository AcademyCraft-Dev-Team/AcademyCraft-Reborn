package org.academy.internal.client.renderer.effect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerFrameRenderQueueTest {
    @Test
    void consumesAFrameOnlyOnceAndReturnsAReadOnlySnapshot() {
        var level = new Object();
        var queue = new PerFrameRenderQueue<String>();
        queue.beginFrame(level);
        queue.add("wing");

        var consumed = queue.consume(level);

        assertEquals(1, consumed.size());
        assertEquals("wing", consumed.getFirst());
        assertThrows(UnsupportedOperationException.class, () -> consumed.add("other"));
        assertTrue(queue.consume(level).isEmpty());
    }

    @Test
    void beginningAFrameAndChangingLevelClearOldEntries() {
        var firstLevel = new Object();
        var secondLevel = new Object();
        var queue = new PerFrameRenderQueue<String>();
        queue.beginFrame(firstLevel);
        queue.add("old frame");
        queue.beginFrame(firstLevel);

        assertTrue(queue.consume(firstLevel).isEmpty());

        queue.beginFrame(firstLevel);
        queue.add("old level");
        assertTrue(queue.consume(secondLevel).isEmpty());
        assertEquals(0, queue.size());
    }
}
