package org.academy.internal.server.world.level.storage;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MisakaPlayerDeviceAllocationsTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000d4");
    private static final Identifier DESIGNATOR = Identifier.parse("academy:laser_designator");

    @Test
    void neverConfiguredUntilAnyPercentWritten() {
        var data = new MisakaPlayerDeviceAllocations();
        assertTrue(data.neverConfigured(PLAYER));
        data.setLastPercents(PLAYER, Map.of(DESIGNATOR, 40));
        assertFalse(data.neverConfigured(PLAYER));
        assertEquals(40, data.lastPercent(PLAYER, DESIGNATOR));
    }

    @Test
    void zeroDoesNotCountAsConfigured() {
        var data = new MisakaPlayerDeviceAllocations();
        data.setLastPercents(PLAYER, Map.of(DESIGNATOR, 0));
        assertTrue(data.neverConfigured(PLAYER));
    }
}
