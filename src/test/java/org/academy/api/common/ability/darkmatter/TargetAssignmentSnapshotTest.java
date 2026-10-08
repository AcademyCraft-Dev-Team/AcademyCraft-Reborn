package org.academy.api.common.ability.darkmatter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TargetAssignmentSnapshotTest {
    private static final class Assignment {
        String owner;
        Object target;
        Assignment(String owner, Object target) { this.owner = owner; this.target = target; }
    }

    @Test
    void snapshotPreservesIdentityCountsAndRebuildsAfterOwnerOrTargetChanges() {
        var target = new String("target"); var equalButDistinct = new String("target");
        var first = new Assignment("a", target); var second = new Assignment("a", target);
        var ally = new Assignment("b", target); var other = new Assignment("a", equalButDistinct);
        var sources = List.of(first, second, ally, other, new Assignment("a", null));
        var snapshot = TargetAssignmentSnapshot.capture(sources, row -> row.owner.equals("a"), row -> row.target);
        assertEquals(2, snapshot.count(target)); assertEquals(1, snapshot.count(equalButDistinct));
        assertFalse(snapshot.contains(new String("target")));
        first.owner = "b"; second.target = equalButDistinct;
        var rebuilt = TargetAssignmentSnapshot.capture(sources, row -> row.owner.equals("a"), row -> row.target);
        assertEquals(2, snapshot.count(target));
        assertEquals(0, rebuilt.count(target)); assertEquals(2, rebuilt.count(equalButDistinct));
    }
}
