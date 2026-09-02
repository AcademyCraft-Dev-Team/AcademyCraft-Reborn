package org.academy.internal.server.time;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.academy.AcademyCraft;
import org.academy.api.server.time.TemporalPauseSource;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Persistent server-owned time-control state. */
public final class TemporalSavedData extends SavedData {
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
    private static final Codec<TemporalPauseSource> SOURCE_CODEC = Codec.STRING.comapFlatMap(
            value -> {
                try {
                    return DataResult.success(TemporalPauseSource.valueOf(
                            value.toUpperCase(Locale.ROOT)
                    ));
                } catch (IllegalArgumentException exception) {
                    return DataResult.error(() -> "Unknown temporal pause source: " + value);
                }
            },
            source -> source.name().toLowerCase(Locale.ROOT)
    );
    private static final Codec<ImmunityEntry> ENTRY_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    UUID_CODEC.fieldOf("entity").forGetter(ImmunityEntry::entityId),
                    SOURCE_CODEC.listOf().fieldOf("sources").forGetter(ImmunityEntry::sources)
            ).apply(instance, ImmunityEntry::new)
    );
    public static final Codec<TemporalSavedData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ENTRY_CODEC.listOf()
                            .optionalFieldOf("persistent_immunities", List.of())
                            .forGetter(TemporalSavedData::entries)
            ).apply(instance, TemporalSavedData::new)
    );
    public static final SavedDataType<TemporalSavedData> SAVED_DATA_TYPE = new SavedDataType<>(
            AcademyCraft.academy("temporal_state"),
            TemporalSavedData::new,
            CODEC
    );

    private final Map<UUID, EnumSet<TemporalPauseSource>> persistentImmunities;

    public TemporalSavedData() {
        persistentImmunities = new HashMap<>();
    }

    private TemporalSavedData(List<ImmunityEntry> entries) {
        persistentImmunities = new HashMap<>();
        for (var entry : entries) {
            if (entry.sources().isEmpty()) continue;
            persistentImmunities.put(
                    entry.entityId(),
                    EnumSet.copyOf(entry.sources())
            );
        }
    }

    public static TemporalSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(SAVED_DATA_TYPE);
    }

    synchronized boolean isImmune(UUID entityId, TemporalPauseSource source) {
        var sources = persistentImmunities.get(entityId);
        return sources != null && sources.contains(source);
    }

    synchronized boolean hasAny(UUID entityId) {
        var sources = persistentImmunities.get(entityId);
        return sources != null && !sources.isEmpty();
    }

    synchronized Set<TemporalPauseSource> sources(UUID entityId) {
        var sources = persistentImmunities.get(entityId);
        return sources == null ? Set.of() : Set.copyOf(sources);
    }

    synchronized Set<UUID> entityIds() {
        return Set.copyOf(persistentImmunities.keySet());
    }

    synchronized boolean setPersistent(
            UUID entityId,
            Set<TemporalPauseSource> sources,
            boolean enabled
    ) {
        if (sources.isEmpty()) return false;

        var changed = false;
        if (enabled) {
            var current = persistentImmunities.computeIfAbsent(
                    entityId,
                    ignored -> EnumSet.noneOf(TemporalPauseSource.class)
            );
            changed = current.addAll(sources);
        } else {
            var current = persistentImmunities.get(entityId);
            if (current != null) {
                changed = current.removeAll(sources);
                if (current.isEmpty()) persistentImmunities.remove(entityId);
            }
        }
        if (changed) setDirty();
        return changed;
    }

    synchronized Map<UUID, Set<TemporalPauseSource>> snapshot() {
        var result = new HashMap<UUID, Set<TemporalPauseSource>>();
        persistentImmunities.forEach((entityId, sources) ->
                result.put(entityId, Set.copyOf(sources)));
        return Map.copyOf(result);
    }

    private synchronized List<ImmunityEntry> entries() {
        var result = new ArrayList<ImmunityEntry>(persistentImmunities.size());
        persistentImmunities.forEach((entityId, sources) ->
                result.add(new ImmunityEntry(entityId, List.copyOf(sources))));
        result.sort((first, second) -> first.entityId().compareTo(second.entityId()));
        return result;
    }

    private record ImmunityEntry(
            UUID entityId,
            List<TemporalPauseSource> sources
    ) {
        private ImmunityEntry {
            sources = List.copyOf(sources);
        }
    }
}
