package org.academy.internal.server.misaka;

import org.academy.AcademyCraft;
import org.academy.internal.common.world.entity.misaka.MisakaSisterEntity;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * O(1) lookup from roster {@code misakaUuid} / serial → loaded entity.
 * Roster identity is not the Minecraft entity UUID, so {@code Level#getEntity} cannot be used;
 * this index is maintained on add/bind/remove instead of scanning the world.
 * <p>
 * Entries are validated on read; {@link #put} drops stale reverse keys when an entity rebinds.
 */
public final class MisakaLoadedSisterIndex {
    private static final Map<UUID, MisakaSisterEntity> BY_MISAKA_UUID = new ConcurrentHashMap<>();
    private static final Map<Integer, MisakaSisterEntity> BY_SERIAL = new ConcurrentHashMap<>();

    private MisakaLoadedSisterIndex() {
    }

    public static void put(MisakaSisterEntity sister) {
        if (sister == null || sister.isRemoved() || sister.level().isClientSide()) {
            return;
        }
        scrubInstance(sister);

        var misakaUuid = sister.getMisakaUuid();
        if (misakaUuid != null) {
            var previous = BY_MISAKA_UUID.put(misakaUuid, sister);
            if (previous != null && previous != sister && !previous.isRemoved()) {
                AcademyCraft.LOGGER.warn(
                        "Misaka index: uuid {} claimed by two loaded entities (keeping #{}, dropping #{})",
                        misakaUuid,
                        sister.getSerial(),
                        previous.getSerial()
                );
            }
        }

        int serial = sister.getSerial();
        if (serial > 0) {
            var previous = BY_SERIAL.put(serial, sister);
            if (previous != null && previous != sister && !previous.isRemoved()) {
                AcademyCraft.LOGGER.warn(
                        "Misaka index: serial #{} claimed by two loaded entities (uuid {} vs {})",
                        serial,
                        sister.getMisakaUuid(),
                        previous.getMisakaUuid()
                );
            }
        }
    }

    public static void remove(MisakaSisterEntity sister) {
        if (sister == null) {
            return;
        }
        scrubInstance(sister);
    }

    public static @Nullable MisakaSisterEntity getByMisakaUuid(UUID misakaUuid) {
        if (misakaUuid == null) {
            return null;
        }
        var sister = BY_MISAKA_UUID.get(misakaUuid);
        if (sister == null) {
            return null;
        }
        if (!isUsable(sister) || !misakaUuid.equals(sister.getMisakaUuid())) {
            BY_MISAKA_UUID.remove(misakaUuid, sister);
            return null;
        }
        return sister;
    }

    public static @Nullable MisakaSisterEntity getBySerial(int serial) {
        if (serial <= 0) {
            return null;
        }
        var sister = BY_SERIAL.get(serial);
        if (sister == null) {
            return null;
        }
        if (!isUsable(sister) || sister.getSerial() != serial) {
            BY_SERIAL.remove(serial, sister);
            return null;
        }
        return sister;
    }

    /**
     * Resolve a roster row to a loaded entity. Uuid match wins; serial is the rebound fallback
     * when the living entity lost {@code academy_misaka_uuid} but kept its serial.
     */
    public static @Nullable MisakaSisterEntity get(UUID misakaUuid, int serial) {
        var byUuid = getByMisakaUuid(misakaUuid);
        if (byUuid != null) {
            return byUuid;
        }
        var bySerial = getBySerial(serial);
        if (bySerial == null) {
            return null;
        }
        // Serial hit with a different live uuid: still return so callers can rebind to roster.
        return bySerial;
    }

    public static Collection<MisakaSisterEntity> all() {
        var snapshot = new ArrayList<MisakaSisterEntity>();
        var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (var entry : BY_MISAKA_UUID.entrySet()) {
            var sister = entry.getValue();
            if (!isUsable(sister) || !entry.getKey().equals(sister.getMisakaUuid())) {
                BY_MISAKA_UUID.remove(entry.getKey(), sister);
                continue;
            }
            if (seen.add(sister)) {
                snapshot.add(sister);
            }
        }
        for (var entry : BY_SERIAL.entrySet()) {
            var sister = entry.getValue();
            if (!isUsable(sister) || sister.getSerial() != entry.getKey()) {
                BY_SERIAL.remove(entry.getKey(), sister);
                continue;
            }
            if (seen.add(sister)) {
                snapshot.add(sister);
            }
        }
        return snapshot;
    }

    private static void scrubInstance(MisakaSisterEntity sister) {
        BY_MISAKA_UUID.entrySet().removeIf(entry -> entry.getValue() == sister);
        BY_SERIAL.entrySet().removeIf(entry -> entry.getValue() == sister);
    }

    private static boolean isUsable(MisakaSisterEntity sister) {
        return sister != null && !sister.isRemoved() && !sister.level().isClientSide();
    }
}
