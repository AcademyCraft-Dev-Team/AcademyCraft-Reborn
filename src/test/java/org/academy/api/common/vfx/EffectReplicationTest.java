package org.academy.api.common.vfx;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EffectReplicationTest {
    @Test void hideCanResumeButEndCannotBeResurrected() {
        var sequence = new EffectSequence(64);
        assertTrue(sequence.accept(1, 1, false));
        assertFalse(sequence.accept(1, 1, false));
        assertTrue(sequence.accept(1, 3, false)); // HIDE
        assertFalse(sequence.accept(1, 2, false));
        assertTrue(sequence.accept(1, 4, false)); // current SNAPSHOT on re-entry
        assertTrue(sequence.accept(1, 5, true)); // END
        assertFalse(sequence.accept(1, 6, false));
        assertTrue(sequence.accept(2, 1, true)); // independent IMPACT even without START
        assertFalse(sequence.accept(2, 1, true));
        sequence.clear();
        assertTrue(sequence.accept(1, 1, false));
    }

    @Test void coalescesStateWithoutStarvingOtherEffects() {
        var queue = new EffectUpdateQueue<String>(8);
        queue.offer(1, "old", 0);
        queue.offer(2, "other", 0);
        queue.offer(1, "latest", 1);
        var out = new ArrayList<String>();
        queue.drain(1, 20, 1, out::add);
        assertEquals(List.of("latest"), out);
        queue.drain(2, 20, 1, out::add);
        assertEquals(List.of("latest", "other"), out);
    }

    @Test void endCancelsQueuedStateAndStaleWorkDoesNotAccumulate() {
        var queue = new EffectUpdateQueue<String>(2);
        queue.offer(1, "obsolete", 0);
        queue.cancel(1);
        queue.offer(2, "expired", 0);
        queue.offer(3, "current", 30);
        var out = new ArrayList<String>();
        queue.drain(30, 20, 8, out::add);
        assertEquals(List.of("current"), out);
        assertEquals(1, queue.dropped());
        assertEquals(0, queue.size());
        queue.offer(4, "a", 40); queue.offer(5, "b", 40); queue.offer(6, "c", 40);
        assertEquals(2, queue.size());
        assertEquals(2, queue.dropped());
    }
}
