package org.academy.internal.common.structure;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.academy.api.common.structure.BlockStructureCollision;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Runtime broad-phase index and scoped exclusions for structure collisions. */
public final class BlockStructureCollisionRuntime {
    private static final ThreadLocal<BlockStructureCollision> IGNORED = new ThreadLocal<>();
    private static final Map<Level, BlockStructureSpatialIndex<Entity>> LEVEL_INDEXES =
            new IdentityHashMap<>();

    private BlockStructureCollisionRuntime() {
    }

    public static boolean isIgnored(BlockStructureCollision collision) {
        return collision != null && IGNORED.get() == collision;
    }

    public static synchronized void update(Entity entity) {
        if (!(entity instanceof BlockStructureCollision)
                || entity.isRemoved()
                || !entity.isAddedToLevel()) {
            unregister(entity);
            return;
        }
        LEVEL_INDEXES.computeIfAbsent(
                entity.level(),
                ignored -> new BlockStructureSpatialIndex<>()
        ).update(entity, entity.getBoundingBox());
    }

    public static synchronized void unregister(Entity entity) {
        if (entity == null) return;
        var level = entity.level();
        var index = LEVEL_INDEXES.get(level);
        if (index == null) return;
        index.remove(entity);
        if (index.isEmpty()) LEVEL_INDEXES.remove(level);
    }

    public static synchronized List<Entity> query(Level level, AABB bounds) {
        if (level == null || bounds == null) return List.of();
        var index = LEVEL_INDEXES.get(level);
        if (index == null) return List.of();
        var candidates = index.query(bounds);
        for (var entity : candidates) {
            if (entity.isRemoved() || !entity.isAddedToLevel() || entity.level() != level) {
                index.remove(entity);
            }
        }
        if (index.isEmpty()) LEVEL_INDEXES.remove(level);
        return candidates.stream()
                .filter(entity -> !entity.isRemoved()
                        && entity.isAddedToLevel()
                        && entity.level() == level)
                .toList();
    }

    public static void runIgnoring(BlockStructureCollision collision, Runnable action) {
        if (collision == null || action == null) {
            throw new IllegalArgumentException("collision and action cannot be null");
        }
        var previous = IGNORED.get();
        IGNORED.set(collision);
        try {
            action.run();
        } finally {
            if (previous == null) IGNORED.remove();
            else IGNORED.set(previous);
        }
    }
}
