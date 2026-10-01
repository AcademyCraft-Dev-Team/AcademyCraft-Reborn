package org.academy.api.common.entitycontrol;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FairWorkQueueTest {
    @Test void scarceScanSlotsRotateEvenWhenEveryWorkerGetsATurn() {
        var queue = new FairWorkQueue<Integer>();
        for (int group = 0; group < 32; group++) for (int worker = 0; worker < 4; worker++) {
            queue.add("owner", group, group * 4 + worker);
        }
        var services = new int[32];
        for (int tick = 0; tick < 256; tick++) {
            queue.rotateStart();
            var visitedGroups = new HashSet<Integer>();
            for (int turn = 0; turn < 256; turn++) {
                int group = queue.next() / 4;
                if (visitedGroups.add(group) && visitedGroups.size() <= 8) services[group]++;
            }
        }
        for (int service : services) assertEquals(64, service, "A group starved behind a full classification budget");
    }
    @Test void splittingOrdersDoesNotStealAnotherControllersTurns() {
        var queue = new FairWorkQueue<String>();
        for (int i = 0; i < 100; i++) queue.add("a", i, "a" + i);
        queue.add("b", "only", "b");
        var seen = new HashSet<String>();
        for (int i = 0; i < 200; i++) {
            var task = queue.next();
            if (i % 2 == 1) assertEquals("b", task);
            else seen.add(task);
        }
        assertEquals(100, seen.size());
    }
    @Test void removalAndReassignmentLeaveNoStaleEntries() {
        var queue = new FairWorkQueue<String>();
        queue.add("a", "x", "one");
        queue.add("a", "x", "two");
        queue.add("b", "y", "one");
        assertEquals(2, queue.size());
        queue.remove("two");
        for (int i = 0; i < 5; i++) assertEquals("one", queue.next());
        queue.remove("one");
        assertNull(queue.next());
        assertEquals(0, queue.size());
    }
}
