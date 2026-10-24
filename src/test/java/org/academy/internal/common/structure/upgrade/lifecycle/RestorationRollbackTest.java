package org.academy.internal.common.structure.upgrade.lifecycle;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RestorationRollbackTest {
    private static final List<Integer> CELLS = List.of(1, 2, 3);

    @Test
    void missingOrForeignTargetRetainsEveryOtherTargetAndSourceOwnership() {
        for (var changed : List.of("air", "foreign")) {
            var world = new HashMap<>(Map.of(1, "placed", 2, "placed", 3, changed));
            var original = Map.copyOf(world);
            var saves = new AtomicInteger();
            assertFalse(RestorationRollback.clear(CELLS, cell -> world.get(cell).equals("placed"),
                    cell -> { world.put(cell, "air"); return true; }, () -> { saves.incrementAndGet(); return true; }));
            assertEquals(original, world);
            assertEquals(0, saves.get());
        }
    }

    @Test
    void completeImageReleasesSourceOnlyAfterClearAndSuccessfulSave() {
        var world = new HashMap<>(Map.of(1, "placed", 2, "placed", 3, "placed"));
        var saves = new AtomicInteger();
        assertTrue(RestorationRollback.clear(CELLS, cell -> world.get(cell).equals("placed"),
                cell -> { world.put(cell, "air"); return true; }, () -> {
                    assertTrue(world.values().stream().allMatch("air"::equals));
                    saves.incrementAndGet();
                    return true;
                }));
        assertEquals(1, saves.get());
    }

    @Test
    void failedDurabilityDoesNotTurnMissingCellsIntoAnIdempotentSuccess() {
        var world = new HashMap<>(Map.of(1, "placed", 2, "placed", 3, "placed"));
        var saves = new AtomicInteger();
        assertFalse(RestorationRollback.clear(CELLS, cell -> world.get(cell).equals("placed"),
                cell -> { world.put(cell, "air"); return true; }, () -> { saves.incrementAndGet(); return false; }));
        assertFalse(RestorationRollback.clear(CELLS, cell -> world.get(cell).equals("placed"),
                cell -> { fail("Unverified retry must not mutate"); return true; }, () -> { saves.incrementAndGet(); return true; }));
        assertEquals(1, saves.get());
    }
}
