package org.academy.api.server.ability;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class SectionEntityIndexTest {
    @Test void sparseMaximumRangeFindsTargetsAcrossPositiveAndNegativeSectionBoundaries() {
        var index = new SectionEntityIndex<Integer>();
        double[][] points = {{399, 0, 0}, {-399, 0, 0}, {0, 399, 0}, {0, -399, 0}, {0, 0, 399}, {0, 0, -399}};
        for (int i = 0; i < points.length; i++) index.update(i, points[i][0], points[i][1], points[i][2], i);
        var cursor = index.cursor(.5, .5, .5, 400);
        var found = new HashSet<Integer>();
        int probes = 0;
        while (!cursor.finished()) {
            var value = cursor.next(); if (value != null) found.add(value);
            assertTrue(++probes < 150_000);
        }
        assertEquals(6, found.size());
    }
    @Test void denseBucketCanResumeWithoutTruncationOrConcurrentModification() {
        var index = new SectionEntityIndex<Integer>();
        for (int id = 0; id < 4224; id++) index.update(id, -1, 64, -1, id);
        var cursor = index.cursor(-1, 64, -1, 24);
        var found = new HashSet<Integer>();
        int calls = 0;
        while (!cursor.finished()) {
            var value = cursor.next();
            if (value != null) { assertTrue(found.add(value)); index.remove(value); }
            assertTrue(++calls < 6000);
        }
        assertEquals(4224, found.size());
        assertEquals(0, index.size());
    }
    @Test void concentricShellsCoverAllOctantsOnceAndBeginAtTheOwner() {
        var index = new SectionEntityIndex<Integer>();
        int id = 0;
        for (int x = -4; x <= 4; x++) for (int y = -4; y <= 4; y++) for (int z = -4; z <= 4; z++) {
            index.update(id, x * 16, y * 16, z * 16, id); id++;
        }
        var cursor = index.cursor(0, 0, 0, 64);
        assertEquals(364, cursor.next());
        var found = new HashSet<Integer>(); found.add(364);
        while (!cursor.finished()) { var value = cursor.next(); if (value != null) assertTrue(found.add(value)); }
        assertEquals(729, found.size());
    }
    @Test void movementIdReuseAndLocalProtectionNeverReturnTheOldIdentity() {
        var index = new SectionEntityIndex<Object>();
        var old = new Object(); var replacement = new Object();
        index.update(1, 0, 0, 0, old);
        index.update(1, 0, 0, 0, replacement);
        assertTrue(index.anyInBox(0, 0, 0, 3, value -> value == replacement));
        assertFalse(index.anyInBox(0, 0, 0, 3, value -> value == old));
        index.update(1, 32, 0, 0, replacement);
        assertFalse(index.anyInBox(0, 0, 0, 3, value -> true));
        assertTrue(index.anyInBox(31, 0, 0, 3, value -> true));
        index.remove(1);
        assertFalse(index.anyInBox(31, 0, 0, 3, value -> true));
    }
}
