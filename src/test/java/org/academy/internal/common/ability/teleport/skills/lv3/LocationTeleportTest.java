package org.academy.internal.common.ability.teleport.skills.lv3;

import net.minecraft.core.BlockPos;
import org.academy.internal.common.skilldata.LocationTeleportData;
import org.academy.internal.common.skilldata.LocationTeleportData.Mark;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocationTeleportTest {
    @Test
    void deathMarkContainsTheDeathDateAndTime() {
        assertEquals(
                "死亡地点 2026-08-09 18:30:45",
                LocationTeleport.deathMarkName(LocalDateTime.of(2026, 8, 9, 18, 30, 45))
        );
    }

    @Test
    void aFullListReplacesTheOldestAutomaticDeathMarkOnly() {
        var data = new LocationTeleportData();
        data.getMarks().add(new Mark(
                "死亡地点 2026-01-01 00:00:00",
                "minecraft:overworld",
                1, 2, 3
        ));
        for (var index = 1; index < LocationTeleport.MAX_MARKS; index++) {
            data.getMarks().add(new Mark(
                    "手动坐标 " + index,
                    "minecraft:overworld",
                    index, 64, index
            ));
        }

        assertTrue(LocationTeleport.addDeathMark(
                data,
                "minecraft:the_nether",
                new BlockPos(7, 8, 9),
                LocalDateTime.of(2026, 8, 9, 18, 30, 45)
        ));
        assertEquals(LocationTeleport.MAX_MARKS, data.getMarks().size());
        assertFalse(data.getMarks().stream().anyMatch(mark ->
                mark.name().equals("死亡地点 2026-01-01 00:00:00")));
        var replacement = data.getMarks().getLast();
        assertEquals("死亡地点 2026-08-09 18:30:45", replacement.name());
        assertEquals("minecraft:the_nether", replacement.dimension());
        assertEquals(7, replacement.x());
        assertEquals(8, replacement.y());
        assertEquals(9, replacement.z());
    }
}
