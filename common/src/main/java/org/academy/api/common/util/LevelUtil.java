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
        if (blockState.isAir() || blockState.getDestroySpeed(null, null) < 0) return false;
        return switch (miningLevel) {
            case 3 ->
                    blockState.is(BlockTags.NEEDS_DIAMOND_TOOL) || blockState.is(BlockTags.NEEDS_IRON_TOOL) || blockState.is(BlockTags.NEEDS_STONE_TOOL) || !blockState.is(BlockTags.MINEABLE_WITH_PICKAXE) && !blockState.is(BlockTags.MINEABLE_WITH_AXE) && !blockState.is(BlockTags.MINEABLE_WITH_SHOVEL) && !blockState.is(BlockTags.MINEABLE_WITH_HOE);
            case 2 ->
                    blockState.is(BlockTags.NEEDS_IRON_TOOL) || blockState.is(BlockTags.NEEDS_STONE_TOOL) || !blockState.is(BlockTags.MINEABLE_WITH_PICKAXE) && !blockState.is(BlockTags.MINEABLE_WITH_AXE) && !blockState.is(BlockTags.MINEABLE_WITH_SHOVEL) && !blockState.is(BlockTags.MINEABLE_WITH_HOE);
            case 1 ->
                    blockState.is(BlockTags.NEEDS_STONE_TOOL) || !blockState.is(BlockTags.MINEABLE_WITH_PICKAXE) && !blockState.is(BlockTags.MINEABLE_WITH_AXE) && !blockState.is(BlockTags.MINEABLE_WITH_SHOVEL) && !blockState.is(BlockTags.MINEABLE_WITH_HOE);
            case 0 ->
                    !blockState.is(BlockTags.NEEDS_DIAMOND_TOOL) && !blockState.is(BlockTags.NEEDS_IRON_TOOL) && !blockState.is(BlockTags.NEEDS_STONE_TOOL);
            default ->
                    !blockState.is(BlockTags.NEEDS_DIAMOND_TOOL) && !blockState.is(BlockTags.NEEDS_IRON_TOOL) && !blockState.is(BlockTags.NEEDS_STONE_TOOL);
        };
    }


    public static Pair<Boolean, Double> destroyBlocksAlongPath(Level level, Vec3 start, Vec3 end, float radius, int miningLevel, boolean dropBlock, boolean spawnParticles, boolean canBlock) {
        final BlockState air = Blocks.AIR.defaultBlockState();
        Set<BlockPos> processedBlocks = new HashSet<>();
        double pathLength = start.distanceTo(end);


        if (pathLength < 1.0E-6) {
            return Pair.of(false, 0.0);
        }

        Vec3 direction = end.subtract(start).normalize();
        int maxSteps = Mth.ceil(pathLength / 0.5);
        BlockPos.MutableBlockPos currentBlockPos = new BlockPos.MutableBlockPos();
        int searchBounds = Mth.ceil(radius); // Renamed for clarity, effectively the same calculation

        for (int step = 0; step <= maxSteps; ++step) {

            double distAlongPath = (step / (double) maxSteps) * pathLength;
            distAlongPath = Math.min(distAlongPath, pathLength); // Clamp distance
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
                                    if (level instanceof ServerLevel serverLevel) {
                                        processedBlocks.add(currentBlockPos.immutable());
                                        BlockEntity blockEntity = blockState.hasBlockEntity() ? serverLevel.getBlockEntity(currentBlockPos) : null;
                                        if (dropBlock) {
                                            Block.dropResources(blockState, serverLevel, currentBlockPos, blockEntity, null, ItemStack.EMPTY);
                                        }
                                        serverLevel.setBlock(currentBlockPos, air, Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
                                        if (spawnParticles) {
                                            serverLevel.levelEvent(2001, currentBlockPos, Block.getId(blockState));
                                        }
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

            if(canBlock && !processedBlocks.contains(centerBlock)) {
                BlockState centerBlockState = level.getBlockState(centerBlock);
                if(!centerBlockState.isAir() && !canBreakBlock(centerBlockState, miningLevel) && isBlockIntersectingCylinder(centerBlock, start, end, radius)) {
                    double blockDistance = calculateDistanceToBlockIntersection(start, direction, pathLength, centerBlock);
                    return Pair.of(true, Math.min(blockDistance, pathLength));
                }
            }
        }

        return Pair.of(false, pathLength);
    }


    private static boolean isBlockIntersectingCylinder(BlockPos blockPos, Vec3 start, Vec3 end, float radius) {
        Vec3 blockCenter = Vec3.atCenterOf(blockPos);
        double distSq = distanceSqToLineSegment(blockCenter, start, end);
        double checkRadius = radius + 0.8660254;
        return distSq <= checkRadius * checkRadius;
    }


    private static double distanceSqToLineSegment(Vec3 point, Vec3 segStart, Vec3 segEnd) {
        Vec3 segment = segEnd.subtract(segStart);
        double segLengthSq = segment.lengthSqr();
        if (segLengthSq < 1.0E-12) {
            return point.distanceToSqr(segStart);
        }

        double t = point.subtract(segStart).dot(segment) / segLengthSq;
        t = Mth.clamp(t, 0.0, 1.0);
        Vec3 closestPoint = segStart.add(segment.scale(t));
        return point.distanceToSqr(closestPoint);
    }


    private static double calculateDistanceToBlockIntersection(Vec3 start, Vec3 direction, double totalPathLength, BlockPos blockPos) {
        Vec3 blockCenter = Vec3.atCenterOf(blockPos);
        Vec3 startToBlock = blockCenter.subtract(start);
        double distanceAlongPath = startToBlock.dot(direction);

        return Mth.clamp(distanceAlongPath, 0.0, totalPathLength);
    }


    public static void attackEntitiesAlongPath(Level level, Vec3 start, Vec3 end, float size, DamageSource damageSource, float damage) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        Vec3 direction = end.subtract(start).normalize();
        double totalDistance = start.distanceTo(end);
        double stepSize = 0.5;

        Set<Entity> damagedEntities = new HashSet<>();

        AABB pathBoundingBox = new AABB(start, end).inflate(size + 1.0);
        List<Entity> nearbyEntities = serverLevel.getEntities((Entity)null, pathBoundingBox, entity ->
                entity != null && entity.isAlive() && !(entity instanceof EnderDragon)
                        && entity.getType() != EntityTypes.HIGH_SPEED_ELECTRON_BEAM_ENTITY_TYPE
        );
        List<EnderDragon> nearbyDragons = serverLevel.getEntitiesOfClass(EnderDragon.class, pathBoundingBox, Entity::isAlive);

        double sizeSq = size * size;

        for (double traveled = 0; traveled <= totalDistance; traveled += stepSize) {
            Vec3 currentPos = start.add(direction.scale(traveled));

            for (Entity entity : nearbyEntities) {
                if (damagedEntities.contains(entity)) continue;

                if (distanceSqToLineSegment(entity.position(), start, end) <= sizeSq) {
                    if (entity.getBoundingBox().intersects(currentPos, currentPos)) {
                        entity.hurt(damageSource, damage);
                        damagedEntities.add(entity);
                    } else {
                        Vec3 closestPointInBB = entity.getBoundingBox().clip(currentPos, currentPos.add(direction.scale(1e-6))).orElse(entity.position());
                        if (closestPointInBB.distanceToSqr(currentPos) <= sizeSq) {
                            entity.hurt(damageSource, damage);
                            damagedEntities.add(entity);
                        }
                    }
                }
            }

            for (EnderDragon dragon : nearbyDragons) {
                if (damagedEntities.contains(dragon)) continue;

                if (distanceSqToLineSegment(dragon.position(), start, end) <= sizeSq) {
                    if (dragon.getBoundingBox().intersects(currentPos, currentPos)) {
                        dragon.reallyHurt(damageSource, damage);
                        damagedEntities.add(dragon);
                    } else {
                        Vec3 closestPointInBB = dragon.getBoundingBox().clip(currentPos, currentPos.add(direction.scale(1e-6))).orElse(dragon.position());
                        if (closestPointInBB.distanceToSqr(currentPos) <= sizeSq) {
                            dragon.reallyHurt(damageSource, damage);
                            damagedEntities.add(dragon);
                        }
                    }
                }
            }
        }
    }
}