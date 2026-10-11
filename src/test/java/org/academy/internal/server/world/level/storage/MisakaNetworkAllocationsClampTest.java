package org.academy.internal.server.world.level.storage;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MisakaNetworkAllocationsClampTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-0000000000c3");

    @Test
    void clampCapsSumAt100AndDropsZero() {
        var raw = new LinkedHashMap<UUID, Integer>();
        raw.put(A, 40);
        raw.put(B, 40);
        raw.put(C, 30);
        var clamped = MisakaNetworkAllocations.clampPlayerPercents(raw);
        assertEquals(40, clamped.get(A));
        assertEquals(40, clamped.get(B));
        assertEquals(20, clamped.get(C));
        assertEquals(100, MisakaNetworkAllocations.sum(clamped));
    }

    @Test
    void clampNullOrEmpty() {
        assertTrue(MisakaNetworkAllocations.clampPlayerPercents(null).isEmpty());
        assertTrue(MisakaNetworkAllocations.clampPlayerPercents(Map.of()).isEmpty());
    }

    @Test
    void clampDropsNonPositive() {
        var raw = new LinkedHashMap<UUID, Integer>();
        raw.put(A, 0);
        raw.put(B, -5);
        raw.put(C, 50);
        var clamped = MisakaNetworkAllocations.clampPlayerPercents(raw);
        assertEquals(Map.of(C, 50), clamped);
    }
}
