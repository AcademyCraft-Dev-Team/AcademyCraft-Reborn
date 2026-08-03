package org.academy.internal.common.ability.teleport;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class TeleportSafety {
    private static final int[] HORIZONTAL_X = {0, 1, -1, 0, 0, 2, -2, 1, -1};
    private static final int[] HORIZONTAL_Z = {0, 0, 0, 1, -1, 0, 0, 2, -2};
    private static final int[] VERTICAL = {0, 1, -1, 2, -2};

    private TeleportSafety() {
    }

    /**
     * Finds a nearby loaded, collision-free position without generating chunks.
     */
    public static Vec3 findSafe(Entity entity, Vec3 desired) {
        if (!(entity.level() instanceof ServerLevel level)) return null;
        return findSafe(entity, level, desired);
    }

    public static Vec3 findSafe(Entity entity, ServerLevel level, Vec3 desired) {
        if (!isFinite(desired)) return null;

        var origin = entity.getBoundingBox();
        var position = entity.position();
        for (var yOffset : VERTICAL) {
            for (var i = 0; i < HORIZONTAL_X.length; i++) {
                var candidate = desired.add(HORIZONTAL_X[i], yOffset, HORIZONTAL_Z[i]);
                var blockPos = BlockPos.containing(candidate);
                if (blockPos.getY() < level.getMinY() || blockPos.getY() >= level.getMaxY()) continue;
                if (!level.hasChunkAt(blockPos)) continue;

                var moved = origin.move(candidate.subtract(position));
                if (!level.getWorldBorder().isWithinBounds(moved)) continue;
                if (level.noCollision(entity, moved)) return candidate;
            }
        }
        return null;
    }

    static boolean isFinite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}
