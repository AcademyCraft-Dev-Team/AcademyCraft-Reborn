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
import java.util.WeakHashMap;

/** Level-scoped bridge between the SubLevel collision index and Minecraft collision queries. */
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

    /** Appends exact SubLevel shapes to an existing collision iterable. */
    public static Iterable<VoxelShape> append(Level level, Iterable<VoxelShape> original, AABB bounds) {
        var shapes = query(level, bounds);
        return shapes.isEmpty() ? original : Iterables.concat(original, shapes);
    }

    /** Exact world boxes intersecting the query; empty for an unknown dimension. */
    public static List<VoxelShape> query(Level level, AABB bounds) {
        SubLevelCollisionIndex index;
        synchronized (SubLevelCollisionRuntime.class) {
            index = INDEXES.get(Objects.requireNonNull(level, "level"));
        }
        if (index == null) return List.of();
        var result = new ArrayList<VoxelShape>();
        for (var shape : index.query(bounds)) result.add(Shapes.create(shape.worldBox()));
        return List.copyOf(result);
    }
}
