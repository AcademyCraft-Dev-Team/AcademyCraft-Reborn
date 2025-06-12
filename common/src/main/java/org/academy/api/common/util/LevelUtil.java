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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.world.entity.EntityTypes;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class LevelUtil {
    @SuppressWarnings("resource")
    public static double getValidViewDistance(Entity entity, double targetDistance) {
        Vec3 startPos = entity.position();
        Vec3 direction = Vec3.directionFromRotation(entity.getXRot(), entity.getYRot()).scale(targetDistance);
        Vec3 targetPos = startPos.add(direction);

        HitResult hitResult = entity.level().clip(new ClipContext(startPos, targetPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
        return (hitResult.getType() != HitResult.Type.MISS) ? hitResult.getLocation().distanceTo(startPos) : targetDistance;
    }

    @SuppressWarnings("DataFlowIssue")
    public static boolean canBreakBlock(BlockState blockState, int miningLevel) {
        if (miningLevel == -1|| blockState.getDestroySpeed(null,null) == -1) {
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
        final BlockState air = Blocks.AIR.defaultBlockState();
        Set<BlockPos> processedBlocks = new HashSet<>();
        double pathLength = start.distanceTo(end);

        Vec3 direction = end.subtract(start).normalize();
        int maxSteps = Mth.ceil(pathLength / 0.5);
        BlockPos.MutableBlockPos currentBlockPos = new BlockPos.MutableBlockPos();
        int searchBounds = Mth.ceil(radius);

        for (int step = 0; step <= maxSteps; ++step) {
            double distAlongPath = (step / (double) maxSteps) * pathLength;
            distAlongPath = Math.min(distAlongPath, pathLength);
            Vec3 currentPoint = start.add(direction.scale(distAlongPath));
            BlockPos centerBlock = BlockPos.containing(currentPoint);

            for (int dx = -searchBounds; dx <= searchBounds; ++dx) {
                for (int dy = -searchBounds; dy <= searchBounds; ++dy) {
                    for (int dz = -searchBounds; dz <= searchBounds; ++dz) {
                        currentBlockPos.set(centerBlock.getX() + dx, centerBlock.getY() + dy, centerBlock.getZ() + dz);

                        if (processedBlocks.contains(currentBlockPos)) {
                            continue;
                        }

                        if (isBlockIntersectingCylinder(currentBlockPos, start, end, radius)) {
                            BlockState blockState = level.getBlockState(currentBlockPos);

                            if (!blockState.isAir()) {
                                boolean breakable = canBreakBlock(blockState, miningLevel);
                                if (breakable) {
                                    if (!simulate) {
                                        processedBlocks.add(currentBlockPos.immutable());
                                        BlockEntity blockEntity = blockState.hasBlockEntity() ? level.getBlockEntity(currentBlockPos) : null;
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
                                    double blockedDistance = calculateDistanceToBlockIntersection(start, direction, pathLength, currentBlockPos);
                                    return Pair.of(true, Math.min(blockedDistance, pathLength));
                                }
                            }
                        }
                    }
                }
            }

            if (canBlock && !processedBlocks.contains(centerBlock)) {
                BlockState centerBlockState = level.getBlockState(centerBlock);
                if (!centerBlockState.isAir() && !canBreakBlock(centerBlockState, miningLevel) && isBlockIntersectingCylinder(centerBlock, start, end, radius)) {
                    double blockDistance = calculateDistanceToBlockIntersection(start, direction, pathLength, centerBlock);
                    return Pair.of(true, Math.min(blockDistance, pathLength));
                }
            }
        }

        return Pair.of(false, pathLength);
    }

    public static boolean isBlockIntersectingCylinder(BlockPos blockPos, Vec3 start, Vec3 end, float radius) {
        Vec3 blockCenter = Vec3.atCenterOf(blockPos);
        double distSq = distanceSqToLineSegment(blockCenter, start, end);
        double checkRadius = radius + 0.8660254;
        return distSq <= checkRadius * checkRadius;
    }

    public static double distanceSqToLineSegment(Vec3 point, Vec3 segStart, Vec3 segEnd) {
        double segDx = segEnd.x - segStart.x;
        double segDy = segEnd.y - segStart.y;
        double segDz = segEnd.z - segStart.z;

        double segLengthSq = segDx * segDx + segDy * segDy + segDz * segDz;

        if (segLengthSq < 1.0E-12) {
            return point.distanceToSqr(segStart);
        }

        double pointToStartDx = point.x - segStart.x;
        double pointToStartDy = point.y - segStart.y;
        double pointToStartDz = point.z - segStart.z;

        double dot = pointToStartDx * segDx + pointToStartDy * segDy + pointToStartDz * segDz;
        double t = Math.max(0, Math.min(1, dot / segLengthSq));

        double closestX = segStart.x + t * segDx;
        double closestY = segStart.y + t * segDy;
        double closestZ = segStart.z + t * segDz;

        double finalDx = point.x - closestX;
        double finalDy = point.y - closestY;
        double finalDz = point.z - closestZ;

        return finalDx * finalDx + finalDy * finalDy + finalDz * finalDz;
    }

    public static double calculateDistanceToBlockIntersection(Vec3 start, Vec3 direction,
                                                              double totalPathLength, BlockPos blockPos) {
        Vec3 blockCenter = Vec3.atCenterOf(blockPos);
        Vec3 startToBlock = blockCenter.subtract(start);
        double distanceAlongPath = startToBlock.dot(direction);

        return Mth.clamp(distanceAlongPath, 0.0, totalPathLength);
    }

    public static void attackEntitiesAlongPath(Level level,
                                               Vec3 start,
                                               Vec3 end,
                                               float radius,
                                               DamageSource damageSource,
                                               float damage) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        Vec3 direction = end.subtract(start).normalize();
        double totalDistance = start.distanceTo(end);

        int steps = (int) Math.ceil(totalDistance / (double) radius);

        AABB pathBB = new AABB(start, end).inflate(radius, radius, radius);
        List<Entity> candidates = serverLevel.getEntities(
                (Entity) null,
                pathBB,
                e -> e.isAlive()
                        && e.getType() != EntityTypes.HIGH_SPEED_ELECTRON_BEAM_ENTITY_TYPE
        );

        Set<Entity> hitSet = new HashSet<>();

        for (int i = 0; i <= steps; i++) {
            double distAlong = Math.min(i * (double) radius, totalDistance);
            Vec3 samplePos = start.add(direction.scale(distAlong));

            AABB sliceBB = new AABB(
                    samplePos.subtract(radius, radius, radius),
                    samplePos.add(radius, radius, radius)
            );

            for (Entity e : candidates) {
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