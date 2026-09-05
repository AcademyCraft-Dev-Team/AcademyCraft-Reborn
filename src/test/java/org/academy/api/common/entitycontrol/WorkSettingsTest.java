package org.academy.api.common.entitycontrol;

import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkSettingsTest {
    @Test void roundTripsFiltersTasksAndStoragePoints() {
        var settings = new WorkSettings(WorkSettings.Mode.MINING, false, true, false, true,
                List.of("minecraft:stone", "#minecraft:logs"), Optional.of(new BlockPos(-1, 64, 9)), Optional.empty());
        var encoded = WorkSettings.CODEC.encodeStart(JsonOps.INSTANCE, settings).getOrThrow();
        assertEquals(settings, WorkSettings.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }
    @Test void rejectsMalformedOrUnboundedFilters() {
        assertThrows(IllegalArgumentException.class, () -> new WorkSettings(WorkSettings.Mode.FARMING,
                true, true, true, false, List.of("#Bad Tag"), Optional.empty(), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new WorkSettings(WorkSettings.Mode.MINING,
                true, true, false, false, Collections.nCopies(33, "minecraft:stone"), Optional.empty(), Optional.empty()));
    }
    @Test void settingsOwnTheirFilterList() {
        var filters = new ArrayList<>(List.of("minecraft:stone"));
        var settings = new WorkSettings(WorkSettings.Mode.MINING, true, true, false, false,
                filters, Optional.empty(), Optional.empty());
        filters.clear();
        assertEquals(1, settings.filters().size());
    }
}
