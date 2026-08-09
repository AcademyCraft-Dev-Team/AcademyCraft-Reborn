package org.academy.internal.common.ability.teleport;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;

/** Shared crosshair targeting for Teleport skills. */
public final class TeleportTargeting {
    private static final double HALF_SCAN_WIDTH = 0.5;
    private static final double MIN_DIRECTION_LENGTH_SQUARED = 1.0e-8;

    public static LivingEntity findFirstLivingEntity(LivingEntity source, double maxRange) {
        if (source == null || maxRange <= 0.0) return null;
        var start = source.getEyePosition();
        var direction = source.getLookAngle().normalize();
        if (direction.lengthSqr() < MIN_DIRECTION_LENGTH_SQUARED) return null;

        var fullEnd = start.add(direction.scale(maxRange));
        var blockHit = source.level().clip(new ClipContext(
                start,
                fullEnd,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                source
        ));
        var end = blockHit.getType() == HitResult.Type.MISS
                ? fullEnd
                : blockHit.getLocation();
        var scanBounds = new AABB(start, end).inflate(HALF_SCAN_WIDTH);

        LivingEntity firstTarget = null;
        var firstHitDistanceSquared = Double.POSITIVE_INFINITY;
        for (var candidate : source.level().getEntitiesOfClass(
                LivingEntity.class,
                scanBounds,
                entity -> entity != source && entity.isAlive() && entity.isPickable()
        )) {
            var targetBounds = candidate.getBoundingBox().inflate(HALF_SCAN_WIDTH);
            double hitDistanceSquared;
            if (targetBounds.contains(start)) {
                hitDistanceSquared = 0.0;
            } else {
                var intersection = targetBounds.clip(start, end);
                if (intersection.isEmpty()) continue;
                hitDistanceSquared = start.distanceToSqr(intersection.get());
            }
            if (hitDistanceSquared < firstHitDistanceSquared) {
                firstTarget = candidate;
                firstHitDistanceSquared = hitDistanceSquared;
            }
        }
        return firstTarget;
    }

    private TeleportTargeting() {
    }
}
