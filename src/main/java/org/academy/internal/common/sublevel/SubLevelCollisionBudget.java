package org.academy.internal.common.sublevel;

import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Bounds snapshot collision-box count by spatially coalescing large collider sets. */
public final class SubLevelCollisionBudget {
    public static final int DEFAULT_MAX_BOXES = 4_096;

    private SubLevelCollisionBudget() {
    }

    public static List<AABB> apply(List<AABB> boxes, int maximum) {
        if (maximum < 1) throw new IllegalArgumentException("maximum must be positive");
        if (boxes.size() <= maximum) return List.copyOf(boxes);
        double cell = 4.0;
        for (int attempt = 0; attempt < 32; attempt++) {
            var grouped = new LinkedHashMap<Key, AABB>();
            for (var box : boxes) {
                var center = box.getCenter();
                var key = new Key((int) Math.floor(center.x / cell), (int) Math.floor(center.y / cell),
                        (int) Math.floor(center.z / cell));
                grouped.merge(key, box, SubLevelCollisionBudget::union);
            }
            if (grouped.size() <= maximum) return List.copyOf(grouped.values());
            cell *= 2.0;
        }
        var all = boxes.getFirst();
        for (var box : boxes) all = union(all, box);
        return List.of(all);
    }

    private static AABB union(AABB left, AABB right) {
        return new AABB(Math.min(left.minX, right.minX), Math.min(left.minY, right.minY),
                Math.min(left.minZ, right.minZ), Math.max(left.maxX, right.maxX),
                Math.max(left.maxY, right.maxY), Math.max(left.maxZ, right.maxZ));
    }

    private record Key(int x, int y, int z) {
    }
}
