package org.academy.api.common.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


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
        final var direction = end.subtract(start).normalize();
        final var pathLength = start.distanceTo(end);

        Pair<List<BlockPos>, List<BlockPos>> collectedBlocks = collectBlocksInCylinder(level, start, end, radius, miningLevel, canBlock);
        List<BlockPos> breakableBlocks = collectedBlocks.getLeft();
        List<BlockPos> unbreakableBlocks = collectedBlocks.getRight();

        double minBlockedDist = calculateMinBlockedDistance(unbreakableBlocks, start, direction, pathLength, canBlock);

        if (!simulate) {
            destroyBreakableBlocks(level, breakableBlocks, start, direction, pathLength, minBlockedDist, dropBlock, spawnParticles);
        }

        return Pair.of(minBlockedDist < pathLength, minBlockedDist);
    }

    private static Pair<List<BlockPos>, List<BlockPos>> collectBlocksInCylinder(Level level, Vec3 start, Vec3 end, float radius, int miningLevel, boolean canBlock) {
        List<BlockPos> breakable = new ArrayList<>();
        List<BlockPos> unbreakable = new ArrayList<>();

        final var overallAABB = new AABB(start, end).inflate(radius + 1.0);

        for (BlockPos blockPos : BlockPos.betweenClosed(
                Mth.floor(overallAABB.minX), Mth.floor(overallAABB.minY), Mth.floor(overallAABB.minZ),
                Mth.floor(overallAABB.maxX), Mth.floor(overallAABB.maxY), Mth.floor(overallAABB.maxZ)
        )) {
            processBlockForDestruction(level, blockPos, start, end, radius, miningLevel, canBlock, breakable, unbreakable);
        }

        return Pair.of(breakable, unbreakable);
    }

    private static double calculateMinBlockedDistance(List<BlockPos> unbreakableBlocks, Vec3 start, Vec3 direction, double pathLength, boolean canBlock) {
        if (!canBlock || unbreakableBlocks.isEmpty()) {
            return pathLength;
        }

        double minBlockedDist = pathLength;
        for (BlockPos pos : unbreakableBlocks) {
            minBlockedDist = Math.min(minBlockedDist, calculateDistanceToBlockIntersection(start, direction, pathLength, pos));
        }
        return minBlockedDist;
    }

    private static void destroyBreakableBlocks(Level level, List<BlockPos> breakableBlocks, Vec3 start, Vec3 direction, double pathLength, double minBlockedDist, boolean dropBlock, boolean spawnParticles) {
        final var air = Blocks.AIR.defaultBlockState();
        for (BlockPos pos : breakableBlocks) {
            if (calculateDistanceToBlockIntersection(start, direction, pathLength, pos) < minBlockedDist) {
                var blockState = level.getBlockState(pos);
                var blockEntity = blockState.hasBlockEntity() ? level.getBlockEntity(pos) : null;
                if (dropBlock) {
                    Block.dropResources(blockState, level, pos, blockEntity, null, ItemStack.EMPTY);
                }
                level.setBlock(pos, air, Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
                if (spawnParticles) {
                    level.levelEvent(2001, pos, Block.getId(blockState));
                }
            }
        }
    }

    private static void processBlockForDestruction(Level level, BlockPos currentPos, Vec3 start, Vec3 end, float radius,
                                                   int miningLevel, boolean canBlock, List<BlockPos> breakableBlocks, List<BlockPos> unbreakableBlocks) {
        if (isBlockIntersectingCylinder(currentPos, start, end, radius)) {
            var blockState = level.getBlockState(currentPos);
            if (!blockState.isAir()) {
                if (canBreakBlock(blockState, miningLevel)) {
                    breakableBlocks.add(currentPos.immutable());
                } else if (canBlock) {
                    unbreakableBlocks.add(currentPos.immutable());
                }
            }
        }
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

        var pathBB = new AABB(start, end).inflate(radius, radius, radius);
        var candidates = serverLevel.getEntities(
                (Entity) null,
                pathBB,
                e -> e.isAlive()
                        && e.getType() != EntityTypes.HIGH_SPEED_ELECTRON_BEAM.get()
        );

        var hitSet = new HashSet<Entity>();

        for (var entity : candidates) {
            processEntityForAttack(serverLevel, entity, start, end, radius, damageSource, damage, hitSet);
        }
    }

    private static void processEntityForAttack(ServerLevel serverLevel, Entity entity, Vec3 start, Vec3 end, float radius,
                                               DamageSource damageSource, float damage, Set<Entity> hitSet) {
        if (hitSet.contains(entity)) return;

        var entityBox = entity.getBoundingBox();
        var entityCenter = entityBox.getCenter();
        var distSq = distanceSqToLineSegment(entityCenter, start, end);
        var effectiveRadius = radius + entity.getBbWidth() / 2.0;

        if (distSq <= effectiveRadius * effectiveRadius) {
            entity.hurtServer(serverLevel, damageSource, damage);
            hitSet.add(entity);
        }
    }
}