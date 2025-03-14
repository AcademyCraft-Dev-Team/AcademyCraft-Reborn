package org.academy.api.common.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

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
}