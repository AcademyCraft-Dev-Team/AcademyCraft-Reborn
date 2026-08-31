package org.academy.internal.common.ability.mentalout;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MentaloutRosterPacketsTest {
    @Test
    void rosterEntryNormalizesUntrustedDisplayValues() {
        var entry = new MentaloutRosterPackets.RosterEntry(
                UUID.randomUUID(),
                4,
                "",
                "",
                Float.NaN,
                -5.0f,
                Float.NaN,
                0,
                64,
                0,
                (byte) 0,
                (byte) 0,
                -20
        );

        assertEquals("minecraft:unknown", entry.entityTypeId());
        assertEquals("minecraft:unknown", entry.displayName());
        assertEquals(0.0f, entry.health());
        assertEquals(0.0f, entry.maxHealth());
        assertEquals(Float.MAX_VALUE, entry.distance());
        assertEquals(0, entry.misidentificationTicks());
    }

    @Test
    void fullSyncChunksCannotExceedProtocolLimit() {
        var entry = new MentaloutRosterPackets.RosterEntry(
                UUID.randomUUID(),
                1,
                "minecraft:zombie",
                "Zombie",
                20.0f,
                20.0f,
                3.0f,
                0,
                64,
                0,
                (byte) 0,
                (byte) 0,
                0
        );
        assertThrows(IllegalArgumentException.class, () -> new MentaloutRosterPackets.FullChunkPacket(
                1,
                0,
                Collections.nCopies(MentaloutRosterPackets.MAX_FULL_CHUNK_ENTRIES + 1, entry)
        ));
    }
}
