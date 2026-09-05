package org.academy.internal.server.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.academy.AcademyCraft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToLongBiFunction;

/** Contents belong to the world, and survive transfers of the physical unit between players. */
public final class SpatialStorageSavedData extends SavedData {
    private record Entry(ItemResource resource, long count) {
        private static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ItemResource.CODEC.fieldOf("resource").forGetter(Entry::resource),
                Codec.LONG.validate(value -> value > 0 ? com.mojang.serialization.DataResult.success(value)
                        : com.mojang.serialization.DataResult.error(() -> "Storage count must be positive")).fieldOf("count").forGetter(Entry::count)
        ).apply(instance, Entry::new));
    }

    private record Unit(UUID id, List<Entry> entries) {
        private static final Codec<Unit> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Unit::id),
                Entry.CODEC.listOf().fieldOf("items").forGetter(Unit::entries)
        ).apply(instance, Unit::new));
    }

    public static final Codec<SpatialStorageSavedData> CODEC = Unit.CODEC.listOf().xmap(
            SpatialStorageSavedData::new, SpatialStorageSavedData::units);
    public static final SavedDataType<SpatialStorageSavedData> TYPE = new SavedDataType<>(
            AcademyCraft.academy("spatial_storage"), SpatialStorageSavedData::new, CODEC);
    private final Map<UUID, LinkedHashMap<ItemResource, Long>> contents = new LinkedHashMap<>();
    private final java.util.Set<UUID> transferring = new java.util.HashSet<>();

    public SpatialStorageSavedData() {
    }

    private SpatialStorageSavedData(List<Unit> units) {
        for (var unit : units) {
            var items = new LinkedHashMap<ItemResource, Long>();
            for (var entry : unit.entries()) items.merge(entry.resource(), entry.count(), Math::addExact);
            contents.put(unit.id(), items);
        }
    }

    public static SpatialStorageSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    private List<Unit> units() {
        return contents.entrySet().stream().map(unit -> new Unit(unit.getKey(),
                unit.getValue().entrySet().stream().map(e -> new Entry(e.getKey(), e.getValue())).toList()))
                .toList();
    }

    public void store(UUID id, ItemStack stack) {
        if (stack.isEmpty()) return;
        contents.computeIfAbsent(id, ignored -> new LinkedHashMap<>())
                .merge(ItemResource.of(stack), (long) stack.getCount(), Math::addExact);
        setDirty();
    }

    /** Consume a single matching item, used when a harvesting effect replants its crop. */
    public boolean consumeOne(UUID id, java.util.function.Predicate<ItemResource> predicate) {
        var items = contents.get(id);
        if (items == null || transferring.contains(id)) return false;
        var iterator = items.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!predicate.test(entry.getKey())) continue;
            if (entry.getValue() == 1) iterator.remove();
            else entry.setValue(entry.getValue() - 1);
            setDirty();
            return true;
        }
        return false;
    }

    public long count(UUID id) {
        var items = contents.get(id);
        if (items == null) return 0;
        long total = 0;
        for (long count : items.values()) total = total > Long.MAX_VALUE - count ? Long.MAX_VALUE : total + count;
        return total;
    }

    /** The destination returns the accepted amount; refused contents stay in this unit. */
    public long transfer(UUID id, int maxTypes, ToLongBiFunction<ItemResource, Long> destination) {
        var items = contents.get(id);
        if (items == null || !transferring.add(id)) return 0;
        long total = 0;
        try {
            var entries = new ArrayList<>(items.entrySet());
            int visited = 0;
            for (var entry : entries) {
                if (visited++ >= maxTypes) break;
                long available = entry.getValue();
                long accepted = destination.applyAsLong(entry.getKey(), available);
                if (accepted < 0 || accepted > available) throw new IllegalStateException("Invalid accepted amount");
                // Rotate rejected entries so a capped automatic pass cannot starve later resources.
                long remaining = items.get(entry.getKey()) - accepted;
                items.remove(entry.getKey());
                if (remaining > 0) items.put(entry.getKey(), remaining);
                if (accepted > 0) {
                    setDirty();
                    total = total > Long.MAX_VALUE - accepted ? Long.MAX_VALUE : total + accepted;
                }
            }
            return total;
        } finally {
            transferring.remove(id);
        }
    }
}
