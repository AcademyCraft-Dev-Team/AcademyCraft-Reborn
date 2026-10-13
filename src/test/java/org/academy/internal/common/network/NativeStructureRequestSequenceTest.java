package org.academy.internal.common.network;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class NativeStructureRequestSequenceTest {
    @Test
    void consumePrecedesTheActionSoEvenAFailedActionCannotBeReplayed() {
        var sequence = new NativeStructureRequestSequence();
        var calls = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> {
            if (sequence.consume(7)) {
                calls.incrementAndGet();
                throw new IllegalStateException("action failed after request consumption");
            }
        });
        for (long id : new long[]{7, 6, 1, 0, -1, Long.MIN_VALUE}) {
            if (sequence.consume(id)) calls.incrementAndGet();
        }
        assertEquals(1, calls.get());
        assertTrue(sequence.consume(8));
    }

    @Test
    void repliesMustHaveBeenIssuedAndCannotReplaceANewerResult() {
        var sequence = new NativeStructureRequestSequence();
        assertFalse(sequence.acknowledge(1));
        assertEquals(1, sequence.next());
        assertEquals(2, sequence.next());
        assertFalse(sequence.acknowledge(3));
        assertTrue(sequence.acknowledge(2));
        assertFalse(sequence.acknowledge(1));
        assertFalse(sequence.acknowledge(2));
        assertFalse(sequence.acknowledge(0));
        assertEquals(3, sequence.next());
        assertTrue(sequence.acknowledge(3));
    }

    @Test
    void connectionLifetimesHaveIndependentWatermarksAndNeverWrap() {
        var old = new NativeStructureRequestSequence();
        assertTrue(old.consume(99));
        var fresh = new NativeStructureRequestSequence();
        assertEquals(1, fresh.next());
        assertFalse(fresh.acknowledge(99));
        var exhausted = new NativeStructureRequestSequence(Long.MAX_VALUE - 1);
        assertEquals(Long.MAX_VALUE, exhausted.next());
        assertThrows(IllegalStateException.class, exhausted::next);
        assertFalse(exhausted.consume(Long.MIN_VALUE));
        assertEquals(Long.MAX_VALUE, exhausted.highest());
    }
}
