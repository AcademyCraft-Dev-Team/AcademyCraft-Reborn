package org.academy.internal.common.skilldata;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocationTeleportDataTest {
    @Test
    void migratesLegacySharedSelectionToBothTeleportUses() {
        var data = new Gson().fromJson("""
                {
                  "marks": [
                    {"name":"A","dimension":"minecraft:overworld","x":1,"y":2,"z":3},
                    {"name":"B","dimension":"minecraft:overworld","x":4,"y":5,"z":6}
                  ],
                  "selectedMarkIndex": 1
                }
                """, LocationTeleportData.class);

        assertEquals(1, data.getQuickMarkIndex());
        assertEquals(1, data.getDefensiveMarkIndex());
    }

    @Test
    void adjustsQuickAndDefensiveSelectionsIndependentlyAfterRemoval() {
        var data = new LocationTeleportData();
        data.getMarks().add(new LocationTeleportData.Mark("A", "minecraft:overworld", 1, 2, 3));
        data.getMarks().add(new LocationTeleportData.Mark("B", "minecraft:overworld", 4, 5, 6));
        data.getMarks().add(new LocationTeleportData.Mark("C", "minecraft:overworld", 7, 8, 9));
        data.setQuickMarkIndex(1);
        data.setDefensiveMarkIndex(2);

        data.getMarks().remove(1);
        data.adjustSelectionsAfterRemoval(1);

        assertEquals(-1, data.getQuickMarkIndex());
        assertEquals(1, data.getDefensiveMarkIndex());
    }
}
