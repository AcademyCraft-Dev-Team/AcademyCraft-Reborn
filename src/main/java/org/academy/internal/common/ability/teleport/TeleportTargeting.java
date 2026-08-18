package org.academy.internal.common.ability.teleport;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Shared crosshair targeting for Teleport skills.
 */
public final class TeleportTargeting {
    private static final double HALF_SCAN_WIDTH = 0.5;
    private static final double MIN_DIRECTION_LENGTH_SQUARED = 1.0e-8;
    private static final double PLACEMENT_STEP = 0.25;
    private static final double DISTANCE_EPSILON = 1.0e-6;

    private TeleportTargeting() {
    }

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

    public static @Nullable Vec3 findSelfTeleportCenter(LivingEntity source, double selectedDistance) {
        return findSelfTeleportCenter(source, source.getEyePosition(), source.getLookAngle(), selectedDistance);
    }

    public static @Nullable Vec3 findSelfTeleportCenter(
            LivingEntity source,
            Vec3 eyePosition,
            Vec3 viewDirection,
            double selectedDistance
    ) {
        var direction = normalizedDirection(viewDirection);
        if (direction == null || !validPlacementRequest(eyePosition, selectedDistance)) return null;
        var end = eyePosition.add(direction.scale(selectedDistance));
        var blockHit = source.level().clip(new ClipContext(
                eyePosition,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                source
        ));
        var searchDistance = blockHit.getType() == HitResult.Type.MISS
                ? selectedDistance
                : Math.min(selectedDistance, eyePosition.distanceTo(blockHit.getLocation()));
        for (var distance = searchDistance; distance >= 0.0; distance -= PLACEMENT_STEP) {
            var center = eyePosition.add(direction.scale(distance));
            if (isSafeCenter(source, center)) return center;
        }
        return null;
    }

    public static @Nullable Vec3 findPiercingTeleportCenter(LivingEntity source, double selectedDistance) {
        return findPiercingTeleportCenter(source, source.getEyePosition(), source.getLookAngle(), selectedDistance);
    }

    public static @Nullable Vec3 findPiercingTeleportCenter(
            LivingEntity source,
            Vec3 eyePosition,
            Vec3 viewDirection,
            double selectedDistance
    ) {
        var direction = normalizedDirection(viewDirection);
        if (direction == null || !validPlacementRequest(eyePosition, selectedDistance)) return null;
        var center = eyePosition.add(direction.scale(selectedDistance));
        return isSafeCenter(source, center) ? center : null;
    }

    public static @Nullable Vec3 findDefaultPiercingTeleportCenter(
            LivingEntity source,
            double selectedDistance
    ) {
        return findDefaultPiercingTeleportCenter(
                source, source.getEyePosition(), source.getLookAngle(), selectedDistance);
    }

    public static @Nullable Vec3 findDefaultPiercingTeleportCenter(
            LivingEntity source,
            Vec3 eyePosition,
            Vec3 viewDirection,
            double selectedDistance
    ) {
        var direction = normalizedDirection(viewDirection);
        if (direction == null || !validPlacementRequest(eyePosition, selectedDistance)) return null;
        var end = eyePosition.add(direction.scale(selectedDistance));
        var blockHit = source.level().clip(new ClipContext(
                eyePosition,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                source
        ));
        if (blockHit.getType() == HitResult.Type.MISS) {
            for (var distance = selectedDistance; distance >= 0.0; distance -= PLACEMENT_STEP) {
                var center = eyePosition.add(direction.scale(distance));
                if (isSafeCenter(source, center)) return center;
            }
            return null;
        }

        var obstructionDistance = eyePosition.distanceTo(blockHit.getLocation());
        for (var distance = obstructionDistance + PLACEMENT_STEP;
             distance <= selectedDistance + DISTANCE_EPSILON;
             distance += PLACEMENT_STEP) {
            var center = eyePosition.add(direction.scale(distance));
            if (isSafeCenter(source, center)) return center;
        }
        return null;
    }

    private static boolean validPlacementRequest(Vec3 eyePosition, double selectedDistance) {
        return eyePosition != null
                && Double.isFinite(eyePosition.x())
                && Double.isFinite(eyePosition.y())
                && Double.isFinite(eyePosition.z())
                && Double.isFinite(selectedDistance)
                && selectedDistance >= 0.0;
    }

    private static @Nullable Vec3 normalizedDirection(Vec3 direction) {
        if (direction == null
                || !Double.isFinite(direction.x())
                || !Double.isFinite(direction.y())
                || !Double.isFinite(direction.z())
                || direction.lengthSqr() < MIN_DIRECTION_LENGTH_SQUARED) {
            return null;
        }
        return direction.normalize();
    }

    private static boolean isSafeCenter(LivingEntity source, Vec3 center) {
        if (!source.level().hasChunkAt(BlockPos.containing(center))) return false;
        var dimensions = source.getDimensions(Pose.STANDING);
        var halfWidth = dimensions.width() / 2.0;
        var halfHeight = dimensions.height() / 2.0;
        var box = new AABB(
                center.x - halfWidth, center.y - halfHeight, center.z - halfWidth,
                center.x + halfWidth, center.y + halfHeight, center.z + halfWidth
        );
        return source.level().noCollision(source, box);
    }
}
