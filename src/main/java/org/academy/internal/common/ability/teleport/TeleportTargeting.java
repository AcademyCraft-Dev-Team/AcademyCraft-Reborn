package org.academy.internal.common.ability.teleport;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
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

    private static double distanceToBoxSqr(Vec3 point, AABB box) {
        var dx = Math.max(Math.max(box.minX - point.x, 0.0), point.x - box.maxX);
        var dy = Math.max(Math.max(box.minY - point.y, 0.0), point.y - box.maxY);
        var dz = Math.max(Math.max(box.minZ - point.z, 0.0), point.z - box.maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    public static LivingEntity findFirstLivingEntity(LivingEntity source, double maxRange) {
        if (source == null || maxRange <= 0.0) return null;
        var start = source.getEyePosition();
        var direction = source.getLookAngle().normalize();
        if (direction.lengthSqr() < MIN_DIRECTION_LENGTH_SQUARED) return null;
        var fullEnd = start.add(direction.scale(maxRange));
        LivingEntity firstTarget = null;
        var searchBox = new AABB(start, fullEnd)
                .inflate(0.85, 1.15, 0.85);
        var targetProjection = Double.MAX_VALUE;
        for (var candidate : source.level().getEntitiesOfClass(LivingEntity.class, searchBox,
                entity -> entity != source && entity.isAlive() && !entity.isSpectator())) {
            var candidateBox = candidate.getBoundingBox().inflate(0.2);
            var projection = candidateBox.getCenter().subtract(start).dot(direction);
            if (projection < 0.0 || projection > maxRange || !source.hasLineOfSight(candidate)) continue;
            var closestPoint = start.add(direction.scale(projection));
            if (distanceToBoxSqr(closestPoint, candidateBox)
                    > 0.2 * 0.2) continue;
            if (projection < targetProjection) {
                targetProjection = projection;
                firstTarget = candidate;
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
        if (blockHit instanceof BlockHitResult hit) {
            var dimensions = source.getDimensions(Pose.STANDING);
            var block = hit.getBlockPos();
            var standingCenter = standingCenterAbove(block, dimensions.height());
            if (isSafeCenter(source, standingCenter)) return standingCenter;
        }
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

    public static @Nullable Vec3 findPiercingTeleportCandidate(
            LivingEntity source,
            double selectedDistance,
            boolean useDefaultTarget
    ) {
        return findPiercingTeleportCandidate(
                source,
                source.getEyePosition(),
                source.getLookAngle(),
                selectedDistance,
                useDefaultTarget
        );
    }

    /**
     * Resolves the point Piercing Teleport may use. Collision only controls the
     * cursor's danger state; unlike Self Teleport it does not reject the point.
     */
    public static @Nullable Vec3 findPiercingTeleportCandidate(
            LivingEntity source,
            Vec3 eyePosition,
            Vec3 viewDirection,
            double selectedDistance,
            boolean useDefaultTarget
    ) {
        var safeCenter = useDefaultTarget
                ? findDefaultPiercingTeleportCenter(
                source, eyePosition, viewDirection, selectedDistance)
                : findPiercingTeleportCenter(
                source, eyePosition, viewDirection, selectedDistance);
        if (safeCenter != null) return safeCenter;

        var direction = normalizedDirection(viewDirection);
        if (direction == null || !validPlacementRequest(eyePosition, selectedDistance)) return null;
        var center = eyePosition.add(direction.scale(selectedDistance));
        return source.level().hasChunkAt(BlockPos.containing(center)) ? center : null;
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

    static Vec3 standingCenterAbove(BlockPos block, double entityHeight) {
        return new Vec3(
                block.getX() + 0.5,
                block.getY() + 1.0 + entityHeight / 2.0,
                block.getZ() + 0.5
        );
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

    public static boolean isSafeCenter(LivingEntity source, Vec3 center) {
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
