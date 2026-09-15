package org.academy.api.server.ability.electromaster;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/** Collision-shape support from any direction, without loading chunks. Keep one cache per mover. */
public final class MagneticSupportQuery {
    private @Nullable BlockPos cached;

    public boolean supported(ServerLevel level, AABB bounds, double radius) {
        if (!Double.isFinite(radius) || radius < 0 || radius > 64) return false;
        if (cached != null && supports(level, bounds, cached, radius)) return true;
        var center = BlockPos.containing(bounds.getCenter());
        var reach = (int) Math.ceil(radius + Math.max(bounds.getXsize(), Math.max(bounds.getYsize(), bounds.getZsize())));
        // Visit only the six faces of each shell, keeping an empty-space search cubic.
        var pos = new BlockPos.MutableBlockPos();
        for (var shell = 0; shell <= reach; shell++) {
            for (var x = -shell; x <= shell; x++) {
                for (var z = -shell; z <= shell; z++) {
                    if (test(level, bounds, pos.setWithOffset(center, x, -shell, z), radius)
                            || (shell > 0 && test(level, bounds, pos.setWithOffset(center, x, shell, z), radius))) return true;
                }
            }
            for (var y = -shell + 1; y < shell; y++) {
                for (var z = -shell; z <= shell; z++) {
                    if (test(level, bounds, pos.setWithOffset(center, -shell, y, z), radius)
                            || test(level, bounds, pos.setWithOffset(center, shell, y, z), radius)) return true;
                }
                for (var x = -shell + 1; x < shell; x++) {
                    if (test(level, bounds, pos.setWithOffset(center, x, y, -shell), radius)
                            || test(level, bounds, pos.setWithOffset(center, x, y, shell), radius)) return true;
                }
            }
        }
        // A failed candidate move must not evict the current position's useful support.
        return false;
    }

    private boolean test(ServerLevel level, AABB bounds, BlockPos pos, double radius) {
        if (!supports(level, bounds, pos, radius)) return false;
        cached = pos.immutable();
        return true;
    }

    private static boolean supports(ServerLevel level, AABB bounds, BlockPos pos, double radius) {
        if (!level.hasChunkAt(pos) || distanceSquared(bounds, new AABB(pos)) > radius * radius) return false;
        var shape = level.getBlockState(pos).getCollisionShape(level, pos);
        for (var box : shape.toAabbs()) {
            if (distanceSquared(bounds, box.move(pos)) <= radius * radius) return true;
        }
        return false;
    }

    public static double distanceSquared(AABB first, AABB second) {
        var x = Math.max(0, Math.max(first.minX - second.maxX, second.minX - first.maxX));
        var y = Math.max(0, Math.max(first.minY - second.maxY, second.minY - first.maxY));
        var z = Math.max(0, Math.max(first.minZ - second.maxZ, second.minZ - first.maxZ));
        return x * x + y * y + z * z;
    }
}
