package org.academy.internal.common.sublevel;

import net.minecraft.world.phys.AABB;
import org.academy.api.common.structure.StructurePose;
import org.academy.internal.common.structure.NativeStructureCollisionBoxes;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Per-level index of exact local collision cuboids and their current pose. Entity movement is
 * resolved against the oriented boxes; the world AABB is only a broad-phase bound and a coarse
 * navigation hint. Mutable entries reuse a stable local box list and a lazily built local grid,
 * so a moving structure never rebuilds its geometry.
 */
public final class SubLevelCollisionIndex {
    private static final int LINEAR_THRESHOLD = 96;
    private static final int MAX_CELLS_PER_BOX = 64;
    private final Map<SubLevelId, Entry> entries = new LinkedHashMap<>();

    public synchronized void update(SubLevelId id, List<AABB> localBoxes, StructurePose pose) {
        update(id, localBoxes, pose, pose, -1, -1);
    }

    public synchronized void update(SubLevelId id, List<AABB> localBoxes, StructurePose pose,
                                    long geometryRevision, long poseRevision) {
        update(id, localBoxes, pose, pose, geometryRevision, poseRevision);
    }

    public synchronized void update(SubLevelId id, List<AABB> localBoxes, StructurePose pose,
                                    StructurePose previousPose, long geometryRevision, long poseRevision) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(localBoxes, "localBoxes");
        Objects.requireNonNull(pose, "pose");
        Objects.requireNonNull(previousPose, "previousPose");
        var previous = entries.get(id);
        if (previous != null && previous.localBoxes == localBoxes) {
            previous.updatePose(pose, previousPose, geometryRevision, poseRevision);
            return;
        }
        var boxes = List.copyOf(localBoxes);
        entries.put(id, new Entry(id, boxes, pose, previousPose, geometryRevision, poseRevision));
    }

    public synchronized boolean remove(SubLevelId id) {
        return entries.remove(Objects.requireNonNull(id, "id")) != null;
    }

    /** Entries whose world AABB intersects the query; treat returned data as read-only. */
    public synchronized List<Entry> query(AABB bounds) {
        Objects.requireNonNull(bounds, "bounds");
        var result = new ArrayList<Entry>();
        for (var entry : entries.values()) {
            if (entry.worldBounds() != null && entry.worldBounds().intersects(bounds)) result.add(entry);
        }
        return result;
    }

    /** Coarse world-space boxes for vanilla navigation only; never used for entity contact. */
    public synchronized List<AABB> navigationBoxes(AABB bounds) {
        Objects.requireNonNull(bounds, "bounds");
        var result = new ArrayList<AABB>();
        for (var entry : entries.values()) {
            if (entry.worldBounds() == null || !entry.worldBounds().intersects(bounds)) continue;
            for (var box : entry.navigationBoxes()) {
                if (box.intersects(bounds)) result.add(box);
            }
        }
        return List.copyOf(result);
    }

    public synchronized Optional<Entry> entry(SubLevelId id) {
        return Optional.ofNullable(entries.get(Objects.requireNonNull(id, "id")));
    }

    public synchronized Optional<AABB> worldBounds(SubLevelId id) {
        var entry = entries.get(Objects.requireNonNull(id, "id"));
        return entry == null || entry.worldBounds() == null ? Optional.empty() : Optional.of(entry.worldBounds());
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

    /** Mutable catalog entry owned by one level; pose changes never invalidate local boxes. */
    public static final class Entry {
        private final SubLevelId id;
        private final List<AABB> localBoxes;
        private final AABB localUnion;
        private StructurePose pose;
        private StructurePose previousPose;
        private @Nullable AABB worldBounds;
        private long geometryRevision;
        private long poseRevision;
        private @Nullable StructurePose navigationPose;
        private List<AABB> navigationBoxes = List.of();
        private @Nullable LocalGrid grid;

        private Entry(SubLevelId id, List<AABB> localBoxes, StructurePose pose, StructurePose previousPose,
                      long geometryRevision, long poseRevision) {
            this.id = id;
            this.localBoxes = localBoxes;
            this.localUnion = union(localBoxes);
            this.pose = pose;
            this.previousPose = previousPose;
            this.geometryRevision = geometryRevision;
            this.poseRevision = poseRevision;
            this.worldBounds = localUnion == null ? null : NativeStructureCollisionBoxes.transformBox(localUnion, pose);
        }

        private void updatePose(StructurePose pose, StructurePose previousPose,
                                long geometryRevision, long poseRevision) {
            this.previousPose = previousPose;
            this.geometryRevision = geometryRevision;
            this.poseRevision = poseRevision;
            if (this.pose.equals(pose)) return;
            this.pose = pose;
            this.worldBounds = localUnion == null ? null : NativeStructureCollisionBoxes.transformBox(localUnion, pose);
        }

        public SubLevelId id() { return id; }
        public List<AABB> localBoxes() { return localBoxes; }
        public StructurePose pose() { return pose; }
        public StructurePose previousPose() { return previousPose; }
        public @Nullable AABB worldBounds() { return worldBounds; }
        public long geometryRevision() { return geometryRevision; }
        public long poseRevision() { return poseRevision; }

        /** Local boxes that can intersect the inverse-transformed query; empty for no overlap. */
        public synchronized List<AABB> localBoxesIn(AABB localQuery) {
            if (localBoxes.size() <= LINEAR_THRESHOLD) {
                var result = new ArrayList<AABB>();
                for (var box : localBoxes) if (box.intersects(localQuery)) result.add(box);
                return result;
            }
            if (grid == null) grid = new LocalGrid(localBoxes);
            return grid.query(localQuery);
        }

        private synchronized List<AABB> navigationBoxes() {
            if (navigationPose == null || !navigationPose.equals(pose)) {
                navigationBoxes = NativeStructureCollisionBoxes.worldBoxes(localBoxes, pose);
                navigationPose = pose;
            }
            return navigationBoxes;
        }

        private static AABB union(List<AABB> boxes) {
            AABB result = null;
            for (var box : boxes) result = result == null ? box : result.minmax(box);
            return result;
        }
    }

    /** Bounded 1-block cell index for local collision cuboids. */
    private static final class LocalGrid {
        private final List<AABB> boxes;
        private final Map<Long, IntList> cells = new HashMap<>();
        private final IntList oversized = new IntList();
        private final int[] marks;
        private int stamp;

        private LocalGrid(List<AABB> boxes) {
            this.boxes = boxes;
            this.marks = new int[boxes.size()];
            for (int index = 0; index < boxes.size(); index++) {
                var box = boxes.get(index);
                int minX = (int) Math.floor(box.minX), minY = (int) Math.floor(box.minY);
                int minZ = (int) Math.floor(box.minZ), maxX = (int) Math.floor(box.maxX);
                int maxY = (int) Math.floor(box.maxY), maxZ = (int) Math.floor(box.maxZ);
                long count = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
                if (count <= 0 || count > MAX_CELLS_PER_BOX) {
                    oversized.add(index);
                    continue;
                }
                for (int x = minX; x <= maxX; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            cells.computeIfAbsent(key(x, y, z), ignored -> new IntList()).add(index);
                        }
                    }
                }
            }
        }

        private synchronized List<AABB> query(AABB localQuery) {
            var result = new ArrayList<AABB>();
            int minX = (int) Math.floor(localQuery.minX), minY = (int) Math.floor(localQuery.minY);
            int minZ = (int) Math.floor(localQuery.minZ), maxX = (int) Math.floor(localQuery.maxX);
            int maxY = (int) Math.floor(localQuery.maxY), maxZ = (int) Math.floor(localQuery.maxZ);
            long count = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
            if (count <= 0 || count > 4_096) {
                for (var box : boxes) if (box.intersects(localQuery)) result.add(box);
                return result;
            }
            int current = ++stamp;
            if (current == 0) {
                java.util.Arrays.fill(marks, 0);
                current = ++stamp;
            }
            // Large merged floors/walls deliberately do not populate every grid cell, but
            // still participate in every exact query. They must never silently disappear.
            for (int i = 0; i < oversized.size; i++) {
                var box = boxes.get(oversized.values[i]);
                if (box.intersects(localQuery)) result.add(box);
            }
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        var list = cells.get(key(x, y, z));
                        if (list == null) continue;
                        for (int i = 0; i < list.size; i++) {
                            int index = list.values[i];
                            if (marks[index] == current) continue;
                            marks[index] = current;
                            var box = boxes.get(index);
                            if (box.intersects(localQuery)) result.add(box);
                        }
                    }
                }
            }
            return result;
        }

        private static long key(int x, int y, int z) {
            return ((long) (x & 0x3FFFFF) << 42) | ((long) (y & 0x3FFFFF) << 21) | (z & 0x3FFFFF);
        }
    }

    private static final class IntList {
        private int[] values = new int[4];
        private int size;

        private void add(int value) {
            if (size == values.length) values = java.util.Arrays.copyOf(values, size * 2);
            values[size++] = value;
        }
    }
}
