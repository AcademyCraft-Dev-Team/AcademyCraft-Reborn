package org.academy.internal.server.world.level.storage;

import net.minecraft.util.RandomSource;
import org.academy.internal.common.world.entity.misaka.MisakaPersonality;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MisakaSisterRosterSerialPoolTest {
    @Test
    void deathReleasesSerialBackToPool() {
        var roster = new MisakaSisterRoster();
        var random = RandomSource.create(1L);
        var first = roster.tryRegisterRescued(random, 0).orElseThrow();
        int serial = first.serial;
        assertEquals(1, roster.usedSerialCount());
        assertTrue(roster.release(first.misakaUuid));
        assertTrue(roster.get(first.misakaUuid).isEmpty());
        assertTrue(roster.findBySerial(serial).isEmpty(), "Released serial must leave the used set");
        assertEquals(0, roster.usedSerialCount());
        assertTrue(roster.hasAvailableSerial());
        var second = roster.tryRegisterRescued(random, 1).orElseThrow();
        assertNotEquals(first.misakaUuid, second.misakaUuid);
        assertEquals(1, roster.usedSerialCount());
    }

    @Test
    void uniqueSerialsWhileAlive() {
        var roster = new MisakaSisterRoster();
        var random = RandomSource.create(42L);
        var serials = new HashSet<Integer>();
        int toCreate = 64;
        for (int i = 0; i < toCreate; i++) {
            var record = roster.tryRegisterRescued(random, i).orElseThrow();
            assertTrue(serials.add(record.serial), "Serials must be unique while alive");
            assertTrue(record.serial >= MisakaSisterRoster.SERIAL_MIN);
            assertTrue(record.serial <= MisakaSisterRoster.SERIAL_MAX);
        }
        assertEquals(toCreate, roster.usedSerialCount());
        assertEquals(MisakaSisterRoster.SERIAL_POOL_SIZE - toCreate, roster.availableSerialCount());

        for (var record : roster.all().toArray(MisakaSisterRecord[]::new)) {
            roster.release(record.misakaUuid);
        }
        assertEquals(0, roster.usedSerialCount());
        assertTrue(roster.hasAvailableSerial());
        assertTrue(roster.tryRegisterRescued(random, 0).isPresent());
    }

    @Test
    void exhaustedPoolForbidsRegistration() throws Exception {
        var roster = new MisakaSisterRoster();
        fillEntirePool(roster);
        assertEquals(0, roster.availableSerialCount());
        assertTrue(roster.tryRegisterRescued(RandomSource.create(7L), 0).isEmpty());
        assertThrows(IllegalStateException.class, () -> roster.registerRescued(RandomSource.create(8L), 0));
    }

    @SuppressWarnings("unchecked")
    private static void fillEntirePool(MisakaSisterRoster roster) throws Exception {
        Field byUuidField = MisakaSisterRoster.class.getDeclaredField("byUuid");
        byUuidField.setAccessible(true);
        Map<UUID, MisakaSisterRecord> byUuid = (Map<UUID, MisakaSisterRecord>) byUuidField.get(roster);
        Map<UUID, MisakaSisterRecord> fill = new HashMap<>(MisakaSisterRoster.SERIAL_POOL_SIZE);
        for (int serial = MisakaSisterRoster.SERIAL_MIN; serial <= MisakaSisterRoster.SERIAL_MAX; serial++) {
            var record = new MisakaSisterRecord(UUID.randomUUID(), serial, MisakaPersonality.TIMID, 0);
            fill.put(record.misakaUuid, record);
        }
        byUuid.putAll(fill);
        Field ready = MisakaSisterRoster.class.getDeclaredField("freeSerialsReady");
        ready.setAccessible(true);
        ready.setBoolean(roster, false);
        assertEquals(0, roster.availableSerialCount());
    }
}
