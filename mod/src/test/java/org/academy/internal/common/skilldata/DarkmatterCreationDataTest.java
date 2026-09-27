package org.academy.internal.common.skilldata;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkmatterCreationDataTest {
    @Test
    void addDeduplicatesAndRemoveCleansOwnerList() {
        var data = new DarkmatterCreationData();
        var uuid = UUID.randomUUID();
        data.add(uuid);
        data.add(uuid);
        assertEquals(1, data.getOwnedBeetles().size());
        data.remove(uuid);
        assertTrue(data.getOwnedBeetles().isEmpty());
    }

    @Test
    void ownsExactlyFourBlueprintSlotsAndPersistsSelection() {
        var data = new DarkmatterCreationData();
        assertEquals(4, data.getBlueprints(4).size());
        data.setSelectedSlot(3);
        assertEquals(3, data.getSelectedSlot());
        data.setSelectedSlot(99);
        assertEquals(3, data.getSelectedSlot());
    }

    @Test
    void summonReservationIsReleasedAtMostOnceByUuid() {
        var data = new DarkmatterCreationData();
        var uuid = UUID.randomUUID();
        data.addSummon(uuid, "unit", 25, 2, "minecraft:overworld", 1, 2, 3);
        assertEquals(25.0f, data.removeSummon(uuid), 0.0001f);
        assertEquals(0.0f, data.removeSummon(uuid), 0.0001f);
    }

    @Test
    void rosterRevisionCanAdvanceForAnIncrementalSnapshot() {
        var data = new DarkmatterCreationData();
        var before = data.getRevision();
        data.bumpRevision();
        assertEquals(before + 1, data.getRevision());
    }

    @Test
    void drainingOwnedEntitiesIsAtomicDeduplicatedAndIdempotent() {
        var data = new DarkmatterCreationData();
        var current = UUID.randomUUID();
        var legacy = UUID.randomUUID();
        data.addSummon(current, "current", 25, 0,
                "minecraft:overworld", 1, 2, 3);
        data.add(current);
        data.add(legacy);
        var before = data.getRevision();

        var drained = data.drainOwnedEntities();

        assertEquals(2, drained.size());
        assertTrue(drained.contains(current));
        assertTrue(drained.contains(legacy));
        assertTrue(data.getOwnedBeetles().isEmpty());
        assertEquals(before + 1, data.getRevision());

        var afterFirstDrain = data.getRevision();
        assertTrue(data.drainOwnedEntities().isEmpty());
        assertEquals(afterFirstDrain, data.getRevision());
    }
}
