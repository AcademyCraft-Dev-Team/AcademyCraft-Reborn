package org.academy.api.common.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.BiConsumer;

public class LevelUtil {
    @SuppressWarnings("resource")
    public static double getValidViewDistance(Entity entity, double targetDistance) {
        Level level = entity.level();
        Vec3 startPos = entity.position();

        float yaw = entity.getYRot();
        float pitch = entity.getXRot();
        Vec3 direction = Vec3.directionFromRotation(pitch, yaw);

        Vec3 targetPos = startPos.add(direction.scale(targetDistance));

        ClipContext context = new ClipContext(startPos, targetPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity);
        HitResult hitResult = level.clip(context);

        if (hitResult.getType() != HitResult.Type.MISS) {
            return hitResult.getLocation().distanceTo(startPos);
        }

        return targetDistance;
    }

    private static void traversePath(Level level, Vec3 start, Vec3 end, BiConsumer<Level, Vec3> action) {
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double dz = end.z - start.z;

        int steps = (int) Math.ceil(Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))));
        double stepX = dx / steps;
        double stepY = dy / steps;
        double stepZ = dz / steps;

        double x = start.x;
        double y = start.y;
        double z = start.z;

        for (int i = 0; i <= steps; i++) {
            action.accept(level, new Vec3(x, y, z));

            x += stepX;
            y += stepY;
            z += stepZ;
        }
    }

    public static void destroyBlocksAlongPath(Level level, Vec3 start, Vec3 end, float size) {
        traversePath(level, start, end, (lvl, pos) -> {
            double minX = pos.x - size;
            double maxX = pos.x + size;
            double minY = pos.y - size;
            double maxY = pos.y + size;
            double minZ = pos.z - size;
            double maxZ = pos.z + size;

            for (double bx = minX; bx <= maxX; bx += 0.5) {
                for (double by = minY; by <= maxY; by += 0.5) {
                    for (double bz = minZ; bz <= maxZ; bz += 0.5) {
                        Vec3 blockCenter = new Vec3(Math.floor(bx) + 0.5, Math.floor(by) + 0.5, Math.floor(bz) + 0.5);

                        if (blockCenter.distanceTo(pos) <= size) {
                            lvl.destroyBlock(new BlockPos((int) blockCenter.x, (int) blockCenter.y, (int) blockCenter.z), false);
                        }
                    }
                }
            }
        });
    }

    public static void attackEntitiesAlongPath(Level level, Vec3 start, Vec3 end, float size, float damage) {
        traversePath(level, start, end, (lvl, pos) -> {
            AABB boundingBox = new AABB(pos.x - size, pos.y - size, pos.z - size,
                    pos.x + size, pos.y + size, pos.z + size);
            List<Entity> entities = lvl.getEntities(null, boundingBox);

            for (Entity entity : entities) {
                if (entity instanceof LivingEntity) {
                    entity.hurt(lvl.damageSources().magic(), damage);
                }
            }
        });
    }
}