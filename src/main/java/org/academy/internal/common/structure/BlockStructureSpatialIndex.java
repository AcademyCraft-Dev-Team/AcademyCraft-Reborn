package org.academy.internal.common.structure;

import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Section-spanning broad-phase index for unusually large structure entities. */
final class BlockStructureSpatialIndex<T> {
    private final Map<T, Entry> entries = new IdentityHashMap<>();
    private final Map<Long, Set<T>> sections = new HashMap<>();

    void update(T value, AABB bounds) {
        if (value == null || bounds == null) {
            throw new IllegalArgumentException("value and bounds cannot be null");
        }
        var previous = entries.get(value);
        if (previous != null && sameBounds(previous.bounds(), bounds)) return;
        var sectionKeys = sectionKeys(bounds);
        if (previous != null && Arrays.equals(previous.sectionKeys(), sectionKeys)) {
            entries.put(value, new Entry(bounds, sectionKeys));
            return;
        }
        if (previous != null) removeFromSections(value, previous.sectionKeys());
        entries.put(value, new Entry(bounds, sectionKeys));
        for (var sectionKey : sectionKeys) {
            sections.computeIfAbsent(
                    sectionKey,
                    ignored -> java.util.Collections.newSetFromMap(new IdentityHashMap<>())
            ).add(value);
        }
    }

    void remove(T value) {
        var entry = entries.remove(value);
        if (entry != null) removeFromSections(value, entry.sectionKeys());
    }

    List<T> query(AABB bounds) {
        if (bounds == null) throw new IllegalArgumentException("bounds cannot be null");
        var result = new ArrayList<T>();
        var visited = java.util.Collections.newSetFromMap(new IdentityHashMap<T, Boolean>());
        for (var sectionKey : sectionKeys(bounds)) {
            var candidates = sections.get(sectionKey);
            if (candidates == null) continue;
            for (var candidate : candidates) {
                if (!visited.add(candidate)) continue;
                var entry = entries.get(candidate);
                if (entry != null && entry.bounds().intersects(bounds)) result.add(candidate);
            }
        }
        return List.copyOf(result);
    }

    boolean isEmpty() {
        return entries.isEmpty();
    }

    private void removeFromSections(T value, long[] sectionKeys) {
        for (var sectionKey : sectionKeys) {
            var values = sections.get(sectionKey);
            if (values == null) continue;
            values.remove(value);
            if (values.isEmpty()) sections.remove(sectionKey);
        }
    }

    private static long[] sectionKeys(AABB bounds) {
        var minX = SectionPos.posToSectionCoord(bounds.minX);
        var minY = SectionPos.posToSectionCoord(bounds.minY);
        var minZ = SectionPos.posToSectionCoord(bounds.minZ);
        var maxX = SectionPos.posToSectionCoord(Math.nextDown(bounds.maxX));
        var maxY = SectionPos.posToSectionCoord(Math.nextDown(bounds.maxY));
        var maxZ = SectionPos.posToSectionCoord(Math.nextDown(bounds.maxZ));
        var xCount = (long) maxX - minX + 1L;
        var yCount = (long) maxY - minY + 1L;
        var zCount = (long) maxZ - minZ + 1L;
        var count = Math.multiplyExact(Math.multiplyExact(xCount, yCount), zCount);
        if (count > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("bounds span too many entity sections");
        }
        var result = new long[(int) count];
        var index = 0;
        for (var x = minX; x <= maxX; x++) {
            for (var y = minY; y <= maxY; y++) {
                for (var z = minZ; z <= maxZ; z++) {
                    result[index++] = SectionPos.asLong(x, y, z);
                }
            }
        }
        return result;
    }

    private static boolean sameBounds(AABB first, AABB second) {
        return Double.compare(first.minX, second.minX) == 0
                && Double.compare(first.minY, second.minY) == 0
                && Double.compare(first.minZ, second.minZ) == 0
                && Double.compare(first.maxX, second.maxX) == 0
                && Double.compare(first.maxY, second.maxY) == 0
                && Double.compare(first.maxZ, second.maxZ) == 0;
    }

    private record Entry(AABB bounds, long[] sectionKeys) {
    }
}
