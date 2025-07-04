package org.academy.api.common.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.world.entity.EntityTypes;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashSet;


public class LevelUtil {
    @SuppressWarnings("resource")
    public static double getValidViewDistance(Entity entity, double targetDistance) {
        var startPos = entity.position();
        var direction = Vec3.directionFromRotation(entity.getXRot(), entity.getYRot()).scale(targetDistance);
        var targetPos = startPos.add(direction);

        var hitResult = entity.level().clip(new ClipContext(startPos, targetPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
        return (hitResult.getType() != HitResult.Type.MISS) ? hitResult.getLocation().distanceTo(startPos) : targetDistance;
    }

    @SuppressWarnings("DataFlowIssue")
    public static boolean canBreakBlock(BlockState blockState, int miningLevel) {
        if (miningLevel == -1 || blockState.getDestroySpeed(null, null) == -1) {
            return false;
        }

        return switch (miningLevel) {
            case 3 -> true;
            case 2 -> !blockState.is(BlockTags.NEEDS_DIAMOND_TOOL);
            case 1 -> !blockState.is(BlockTags.NEEDS_IRON_TOOL)
                    && !blockState.is(BlockTags.NEEDS_DIAMOND_TOOL);
            case 0 -> !blockState.is(BlockTags.NEEDS_DIAMOND_TOOL)
                    && !blockState.is(BlockTags.NEEDS_IRON_TOOL)
                    && !blockState.is(BlockTags.NEEDS_STONE_TOOL);
            default -> false;
        };
    }

    public static Pair<Boolean, Double> destroyBlocksAlongPath(Level level, Vec3 start, Vec3 end, float radius,
                                                               int miningLevel, boolean dropBlock,
                                                               boolean spawnParticles, boolean canBlock,
                                                               boolean simulate) {
        final var air = Blocks.AIR.defaultBlockState();
        var processedBlocks = new HashSet<BlockPos>();
        var pathLength = start.distanceTo(end);

        var direction = end.subtract(start).normalize();
        var maxSteps = Mth.ceil(pathLength / 0.5);
        var currentBlockPos = new BlockPos.MutableBlockPos();
        var searchBounds = Mth.ceil(radius);

        for (var step = 0; step <= maxSteps; ++step) {
            var distAlongPath = (step / (double) maxSteps) * pathLength;
            distAlongPath = Math.min(distAlongPath, pathLength);
            var currentPoint = start.add(direction.scale(distAlongPath));
            var centerBlock = BlockPos.containing(currentPoint);

            for (var dx = -searchBounds; dx <= searchBounds; ++dx) {
                for (var dy = -searchBounds; dy <= searchBounds; ++dy) {
                    for (var dz = -searchBounds; dz <= searchBounds; ++dz) {
                        currentBlockPos.set(centerBlock.getX() + dx, centerBlock.getY() + dy, centerBlock.getZ() + dz);

                        if (processedBlocks.contains(currentBlockPos)) {
                            continue;
                        }

                        if (isBlockIntersectingCylinder(currentBlockPos, start, end, radius)) {
                            var blockState = level.getBlockState(currentBlockPos);

                            if (!blockState.isAir()) {
                                var breakable = canBreakBlock(blockState, miningLevel);
                                if (breakable) {
                                    if (!simulate) {
                                        processedBlocks.add(currentBlockPos.immutable());
                                        var blockEntity = blockState.hasBlockEntity() ? level.getBlockEntity(currentBlockPos) : null;
                                        if (dropBlock) {
                                            Block.dropResources(blockState, level, currentBlockPos, blockEntity, null, ItemStack.EMPTY);
                                        }
                                        level.setBlock(currentBlockPos, air, Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
                                        if (spawnParticles) {
                                            level.levelEvent(2001, currentBlockPos, Block.getId(blockState));
                                        }
                                    } else {
                                        processedBlocks.add(currentBlockPos.immutable());
                                    }
                                } else if (canBlock) {
                                    var blockedDistance = calculateDistanceToBlockIntersection(start, direction, pathLength, currentBlockPos);
                                    return Pair.of(true, Math.min(blockedDistance, pathLength));
                                }
                            }
                        }
                    }
                }
            }

            if (canBlock && !processedBlocks.contains(centerBlock)) {
                var centerBlockState = level.getBlockState(centerBlock);
                if (!centerBlockState.isAir() && !canBreakBlock(centerBlockState, miningLevel) && isBlockIntersectingCylinder(centerBlock, start, end, radius)) {
                    var blockDistance = calculateDistanceToBlockIntersection(start, direction, pathLength, centerBlock);
                    return Pair.of(true, Math.min(blockDistance, pathLength));
                }
            }
        }

        return Pair.of(false, pathLength);
    }

    public static boolean isBlockIntersectingCylinder(BlockPos blockPos, Vec3 start, Vec3 end, float radius) {
        var blockCenter = Vec3.atCenterOf(blockPos);
        var distSq = distanceSqToLineSegment(blockCenter, start, end);
        var checkRadius = radius + 0.8660254;
        return distSq <= checkRadius * checkRadius;
    }

    public static double distanceSqToLineSegment(Vec3 point, Vec3 segStart, Vec3 segEnd) {
        var segDx = segEnd.x - segStart.x;
        var segDy = segEnd.y - segStart.y;
        var segDz = segEnd.z - segStart.z;

        var segLengthSq = segDx * segDx + segDy * segDy + segDz * segDz;

        if (segLengthSq < 1.0E-12) {
            return point.distanceToSqr(segStart);
        }

        var pointToStartDx = point.x - segStart.x;
        var pointToStartDy = point.y - segStart.y;
        var pointToStartDz = point.z - segStart.z;

        var dot = pointToStartDx * segDx + pointToStartDy * segDy + pointToStartDz * segDz;
        var t = Math.max(0, Math.min(1, dot / segLengthSq));

        var closestX = segStart.x + t * segDx;
        var closestY = segStart.y + t * segDy;
        var closestZ = segStart.z + t * segDz;

        var finalDx = point.x - closestX;
        var finalDy = point.y - closestY;
        var finalDz = point.z - closestZ;

        return finalDx * finalDx + finalDy * finalDy + finalDz * finalDz;
    }

    public static double calculateDistanceToBlockIntersection(Vec3 start, Vec3 direction,
                                                              double totalPathLength, BlockPos blockPos) {
        var blockCenter = Vec3.atCenterOf(blockPos);
        var startToBlock = blockCenter.subtract(start);
        var distanceAlongPath = startToBlock.dot(direction);

        return Mth.clamp(distanceAlongPath, 0.0, totalPathLength);
    }

    public static void attackEntitiesAlongPath(Level level,
                                               Vec3 start,
                                               Vec3 end,
                                               float radius,
                                               DamageSource damageSource,
                                               float damage) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        var direction = end.subtract(start).normalize();
        var totalDistance = start.distanceTo(end);

        var steps = (int) Math.ceil(totalDistance / (double) radius);

        var pathBB = new AABB(start, end).inflate(radius, radius, radius);
        var candidates = serverLevel.getEntities(
                (Entity) null,
                pathBB,
                e -> e.isAlive()
                        && e.getType() != EntityTypes.HIGH_SPEED_ELECTRON_BEAM_ENTITY_TYPE
        );

        var hitSet = new HashSet<Entity>();

        for (var i = 0; i <= steps; i++) {
            var distAlong = Math.min(i * (double) radius, totalDistance);
            var samplePos = start.add(direction.scale(distAlong));

            var sliceBB = new AABB(
                    samplePos.subtract(radius, radius, radius),
                    samplePos.add(radius, radius, radius)
            );

            for (var e : candidates) {
                if (hitSet.contains(e)) continue;

                if (e.getBoundingBox().intersects(sliceBB)) {
                    if (e instanceof EnderDragon) {
                        ((EnderDragon) e).reallyHurt(damageSource, damage);
                    } else {
                        e.hurt(damageSource, damage);
                    }
                    hitSet.add(e);
                }
            }
        }
    }
}