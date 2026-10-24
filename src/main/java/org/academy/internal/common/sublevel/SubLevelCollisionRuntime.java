package org.academy.internal.common.sublevel;

import com.google.common.collect.Iterables;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Level-scoped bridge between the SubLevel collision index and the host level. Ordinary block
 * collision never receives structure AABBs: entity movement resolves oriented boxes through
 * {@link SubLevelContactResolver}. Only vanilla navigation still consumes a coarse hint.
 */
public final class SubLevelCollisionRuntime {
    private static final Map<Level, SubLevelCollisionIndex> INDEXES = new WeakHashMap<>();

    private SubLevelCollisionRuntime() {
    }

    public static synchronized void register(Level level, SubLevelCollisionIndex index) {
        INDEXES.put(Objects.requireNonNull(level, "level"), Objects.requireNonNull(index, "index"));
    }

    public static synchronized void unregister(Level level, SubLevelCollisionIndex index) {
        INDEXES.remove(level, index);
    }

    public static Optional<SubLevelCollisionIndex> index(Level level) {
        if (level == null) return Optional.empty();
        synchronized (SubLevelCollisionRuntime.class) {
            return Optional.ofNullable(INDEXES.get(level));
        }
    }

    /** Appends coarse SubLevel boxes for vanilla path navigation only. */
    public static Iterable<VoxelShape> appendNavigation(Level level, Iterable<VoxelShape> original, AABB bounds) {
        var shapes = navigationShapes(level, bounds);
        return shapes.isEmpty() ? original : Iterables.concat(original, shapes);
    }

    private static List<VoxelShape> navigationShapes(Level level, AABB bounds) {
        var index = index(level).orElse(null);
        if (index == null) return List.of();
        var boxes = index.navigationBoxes(bounds);
        if (boxes.isEmpty()) return List.of();
        var result = new ArrayList<VoxelShape>(boxes.size());
        for (var box : boxes) result.add(Shapes.create(box));
        return List.copyOf(result);
    }
}
