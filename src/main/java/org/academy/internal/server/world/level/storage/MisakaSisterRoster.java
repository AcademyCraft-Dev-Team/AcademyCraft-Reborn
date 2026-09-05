package org.academy.internal.server.world.level.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.academy.AcademyCraft;
import org.academy.internal.common.world.entity.misaka.MisakaPersonality;
import org.academy.internal.server.misaka.MisakaComputeIndex;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public final class MisakaSisterRoster extends SavedData {
    public static final int SERIAL_MIN = 100_001;
    public static final int SERIAL_MAX = 200_001;
    public static final int SERIAL_POOL_SIZE = SERIAL_MAX - SERIAL_MIN + 1;

    private static final Codec<UUID> UUID_CODEC = Codec.STRING.comapFlatMap(
            value -> {
                try {
                    return DataResult.success(UUID.fromString(value));
                } catch (IllegalArgumentException exception) {
                    return DataResult.error(() -> "Invalid UUID: " + value);
                }
            },
            UUID::toString
    );

    public static final Codec<MisakaSisterRoster> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(UUID_CODEC, MisakaSisterRecord.CODEC)
                    .fieldOf("sisters")
                    .forGetter(MisakaSisterRoster::snapshot)
    ).apply(instance, MisakaSisterRoster::new));

    public static final SavedDataType<MisakaSisterRoster> SAVED_DATA_TYPE = new SavedDataType<>(
            AcademyCraft.academy("misaka_roster"),
            MisakaSisterRoster::new,
            CODEC
    );

    private final Map<UUID, MisakaSisterRecord> byUuid = new HashMap<>();
    /** Lazy free-list of unused serials in [{@link #SERIAL_MIN}, {@link #SERIAL_MAX}]. */
    private final ArrayList<Integer> freeSerials = new ArrayList<>();
    private boolean freeSerialsReady;

    public MisakaSisterRoster() {
    }

    private MisakaSisterRoster(Map<UUID, MisakaSisterRecord> sisters) {
        sisters.forEach((uuid, record) -> byUuid.put(uuid, record));
    }

    public static MisakaSisterRoster get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(SAVED_DATA_TYPE);
    }

    public int usedSerialCount() {
        ensureFreeSerials();
        return SERIAL_POOL_SIZE - freeSerials.size();
    }

    public int availableSerialCount() {
        ensureFreeSerials();
        return freeSerials.size();
    }

    public boolean hasAvailableSerial() {
        return availableSerialCount() > 0;
    }

    /**
     * Registers a rescued sister with a unique UUID and unused serial in
     * [{@link #SERIAL_MIN}, {@link #SERIAL_MAX}]. Empty when the pool is exhausted.
     */
    public Optional<MisakaSisterRecord> tryRegisterRescued(RandomSource random, int dayIndex) {
        ensureFreeSerials();
        if (freeSerials.isEmpty()) {
            return Optional.empty();
        }
        int index = random.nextInt(freeSerials.size());
        int serial = freeSerials.remove(index);
        var record = new MisakaSisterRecord(
                UUID.randomUUID(),
                serial,
                MisakaPersonality.random(random),
                dayIndex
        );
        byUuid.put(record.misakaUuid, record);
        setDirty();
        MisakaComputeIndex.get().markDirty();
        return Optional.of(record);
    }

    public MisakaSisterRecord registerRescued(RandomSource random, int dayIndex) {
        return tryRegisterRescued(random, dayIndex)
                .orElseThrow(() -> new IllegalStateException("Misaka sister serial pool exhausted"));
    }

    /**
     * Removes the sister from the roster so her serial returns to the candidate pool.
     * Call after death favor side-effects have been applied.
     */
    public boolean release(UUID misakaUuid) {
        if (misakaUuid == null) {
            return false;
        }
        var removed = byUuid.remove(misakaUuid);
        if (removed == null) {
            return false;
        }
        ensureFreeSerials();
        if (removed.serial >= SERIAL_MIN && removed.serial <= SERIAL_MAX) {
            freeSerials.add(removed.serial);
        }
        setDirty();
        MisakaComputeIndex.get().markDirty();
        return true;
    }

    public Optional<MisakaSisterRecord> get(UUID misakaUuid) {
        return Optional.ofNullable(byUuid.get(misakaUuid));
    }

    public void modify(UUID misakaUuid, Consumer<MisakaSisterRecord> mutator) {
        var record = byUuid.get(misakaUuid);
        if (record == null) {
            return;
        }
        mutator.accept(record);
        setDirty();
        MisakaComputeIndex.get().markDirty();
    }

    public Collection<MisakaSisterRecord> all() {
        return byUuid.values();
    }

    public Optional<MisakaSisterRecord> findBySerial(int serial) {
        return byUuid.values().stream()
                .filter(record -> record.serial == serial)
                .findFirst();
    }

    private void ensureFreeSerials() {
        if (freeSerialsReady) {
            return;
        }
        var used = new BitSet(SERIAL_POOL_SIZE);
        for (var record : byUuid.values()) {
            if (record.serial >= SERIAL_MIN && record.serial <= SERIAL_MAX) {
                used.set(record.serial - SERIAL_MIN);
            }
        }
        freeSerials.clear();
        freeSerials.ensureCapacity(SERIAL_POOL_SIZE - used.cardinality());
        for (int serial = SERIAL_MIN; serial <= SERIAL_MAX; serial++) {
            if (!used.get(serial - SERIAL_MIN)) {
                freeSerials.add(serial);
            }
        }
        freeSerialsReady = true;
    }

    private Map<UUID, MisakaSisterRecord> snapshot() {
        return Map.copyOf(byUuid);
    }
}
