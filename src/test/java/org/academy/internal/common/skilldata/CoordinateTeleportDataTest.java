package org.academy.internal.common.skilldata;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CoordinateTeleportDataTest {
    @Test
    void savedPositionsAreCappedAtThirtyTwo() {
        var data = new CoordinateTeleportData();
        for (var i = 0; i < 40; i++) {
            data.addPosition(new CoordinateTeleportData.SavedPosition(
                    Integer.toString(i), i, 64, i, "minecraft:overworld"));
        }
        assertEquals(CoordinateTeleportData.MAX_POSITIONS, data.getSavedPositions().size());
        assertEquals("8", data.getSavedPositions().getFirst().name());
        assertEquals("39", data.getSavedPositions().getLast().name());
    }
}
