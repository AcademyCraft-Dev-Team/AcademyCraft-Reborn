package org.academy.internal.common.sublevel;

import net.minecraft.world.phys.AABB;
import org.academy.api.common.structure.StructurePose;
import org.academy.internal.common.structure.NativeStructureCollisionBoxes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Phase 0 collision prototype: a per-level index of exact world-space boxes, independent of any
 * entity bounding box. Phase 2 will feed this index from the level collision mixins.
 */
public final class SubLevelCollisionIndex {
    private final Map<SubLevelId, Entry> entries = new LinkedHashMap<>();

    public synchronized void update(SubLevelId id, List<AABB> localBoxes, StructurePose pose) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(localBoxes, "localBoxes");
        Objects.requireNonNull(pose, "pose");
        var worldBoxes = NativeStructureCollisionBoxes.worldBoxes(List.copyOf(localBoxes), pose);
        AABB union = null;
        for (var box : worldBoxes) union = union == null ? box : union.minmax(box);
        entries.put(id, new Entry(id, List.copyOf(worldBoxes), union, pose));
    }

    public synchronized boolean remove(SubLevelId id) {
        return entries.remove(Objects.requireNonNull(id, "id")) != null;
    }

    /** All exact world boxes intersecting the query; empty for an empty structure or a query gap. */
    public synchronized List<Shape> query(AABB bounds) {
        Objects.requireNonNull(bounds, "bounds");
        var result = new ArrayList<Shape>();
        for (var entry : entries.values()) {
            if (entry.union == null || !entry.union.intersects(bounds)) continue;
            for (var box : entry.worldBoxes) {
                if (box.intersects(bounds)) result.add(new Shape(entry.id, box));
            }
        }
        return List.copyOf(result);
    }

    public synchronized Optional<AABB> worldBounds(SubLevelId id) {
        var entry = entries.get(Objects.requireNonNull(id, "id"));
        return entry == null || entry.union == null ? Optional.empty() : Optional.of(entry.union);
    }

    public synchronized List<SubLevelId> ids() {
        return List.copyOf(entries.keySet());
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized void clear() {
        entries.clear();
    }

    public record Shape(SubLevelId id, AABB worldBox) {
        public Shape {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(worldBox, "worldBox");
        }
    }

    private record Entry(SubLevelId id, List<AABB> worldBoxes, AABB union, StructurePose pose) {
    }
}
