package org.academy.api.common.util;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
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
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
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
            case 1 -> !blockState.is(BlockTags.NEEDS_IRON_TOOL) && !blockState.is(BlockTags.NEEDS_DIAMOND_TOOL);
            case 0 ->
                    !blockState.is(BlockTags.NEEDS_DIAMOND_TOOL) && !blockState.is(BlockTags.NEEDS_IRON_TOOL) && !blockState.is(BlockTags.NEEDS_STONE_TOOL);
            default -> false;
        };
    }

    public static Pair<Boolean, Double> destroyBlocksAlongPath(Level level, Vec3 start, Vec3 end, float radius, int miningLevel, boolean dropBlock, boolean spawnParticles, boolean canBlock, boolean simulate) {
        var pathLength = start.distanceTo(end);
        var collectedBlocks = collectBlocksOptimized(level, start, end, radius, miningLevel, canBlock);
        var breakableBlocks = collectedBlocks.getLeft();
        var unbreakableBlocks = collectedBlocks.getRight();
        var minBlockedDist = calculateMinBlockedDistance(level, unbreakableBlocks, start, end, radius, pathLength, canBlock);

        if (!simulate) {
            destroyBreakableBlocks(level, breakableBlocks, start, end, radius, pathLength, minBlockedDist, dropBlock, spawnParticles);
        }
        return Pair.of(minBlockedDist < pathLength, minBlockedDist);
    }

    private static Pair<List<BlockPos>, List<BlockPos>> collectBlocksOptimized(Level level, Vec3 start, Vec3 end, float radius, int miningLevel, boolean canBlock) {
        var breakable = new ArrayList<BlockPos>();
        var unbreakable = new ArrayList<BlockPos>();

        var dir = end.subtract(start).normalize();
        var length = start.distanceTo(end);
        var step = Math.max(0.5, radius * 0.8);

        var visited = new LongOpenHashSet();
        var mutablePos = new BlockPos.MutableBlockPos();

        ChunkAccess currentChunk = null;
        var currentChunkX = Integer.MAX_VALUE;
        var currentChunkZ = Integer.MAX_VALUE;

        for (double d = 0; d <= length; d += step) {
            var samplePoint = start.add(dir.scale(d));

            var minX = Mth.floor(samplePoint.x - radius);
            var maxX = Mth.floor(samplePoint.x + radius);
            var minY = Mth.floor(samplePoint.y - radius);
            var maxY = Mth.floor(samplePoint.y + radius);
            var minZ = Mth.floor(samplePoint.z - radius);
            var maxZ = Mth.floor(samplePoint.z + radius);

            for (var x = minX; x <= maxX; x++) {
                for (var z = minZ; z <= maxZ; z++) {
                    var chunkX = SectionPos.blockToSectionCoord(x);
                    var chunkZ = SectionPos.blockToSectionCoord(z);

                    if (chunkX != currentChunkX || chunkZ != currentChunkZ) {
                        currentChunk = level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                        currentChunkX = chunkX;
                        currentChunkZ = chunkZ;
                    }

                    if (currentChunk == null) continue;

                    for (var y = minY; y <= maxY; y++) {
                        var posPacked = BlockPos.asLong(x, y, z);
                        if (!visited.add(posPacked)) continue;

                        var blockState = getBlockStateFast(currentChunk, level, x, y, z);

                        if (blockState.isAir()) continue;

                        mutablePos.set(x, y, z);
                        var shape = blockState.getCollisionShape(level, mutablePos);
                        if (shape.isEmpty()) continue;

                        var blockAABB = shape.bounds().move(x, y, z);
                        if (getIntersectionT(start, end, blockAABB.inflate(radius)) <= 1.0) {
                            if (canBreakBlock(blockState, miningLevel)) {
                                breakable.add(mutablePos.immutable());
                            } else if (canBlock) {
                                unbreakable.add(mutablePos.immutable());
                            }
                        }
                    }
                }
            }
        }
        return Pair.of(breakable, unbreakable);
    }

    private static BlockState getBlockStateFast(ChunkAccess chunk, Level level, int x, int y, int z) {
        if (y < level.getMinY() || y >= level.getMaxY()) {
            return Blocks.AIR.defaultBlockState();
        }
        var sectionIndex = level.getSectionIndex(y);
        var section = chunk.getSection(sectionIndex);

        if (section.hasOnlyAir()) {
            return Blocks.AIR.defaultBlockState();
        }

        return section.getBlockState(x & 15, y & 15, z & 15);
    }

    private static double calculateMinBlockedDistance(Level level, List<BlockPos> unbreakableBlocks, Vec3 start, Vec3 end, float radius, double pathLength, boolean canBlock) {
        if (!canBlock || unbreakableBlocks.isEmpty()) {
            return pathLength;
        }
        var minT = 1.0;
        for (var pos : unbreakableBlocks) {
            var shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;
            var t = getIntersectionT(start, end, shape.bounds().move(pos).inflate(radius));
            if (t < minT) {
                minT = t;
            }
        }
        return minT * pathLength;
    }

    private static void destroyBreakableBlocks(Level level, List<BlockPos> breakableBlocks, Vec3 start, Vec3 end, float radius, double pathLength, double minBlockedDist, boolean dropBlock, boolean spawnParticles) {
        var air = Blocks.AIR.defaultBlockState();
        for (var pos : breakableBlocks) {
            var shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;

            var t = getIntersectionT(start, end, shape.bounds().move(pos).inflate(radius));
            var dist = t * pathLength;

            if (dist < minBlockedDist) {
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

    public static void attackEntitiesAlongPath(Level level, Vec3 start, Vec3 end, float radius, DamageSource damageSource, float damage) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        var pathBB = new AABB(start, end).inflate(radius);
        var candidates = serverLevel.getEntities((Entity) null, pathBB, e -> e.isAlive() && e.getType() != EntityTypes.HIGH_SPEED_ELECTRON_BEAM.get());
        var hitSet = new HashSet<Entity>();

        for (var entity : candidates) {
            processEntityForAttack(serverLevel, entity, start, end, radius, damageSource, damage, hitSet);
        }
    }

    private static void processEntityForAttack(ServerLevel serverLevel, Entity entity, Vec3 start, Vec3 end, float radius, DamageSource damageSource, float damage, Set<Entity> hitSet) {
        if (hitSet.contains(entity)) return;

        var expandedBox = entity.getBoundingBox().inflate(radius);
        if (getIntersectionT(start, end, expandedBox) <= 1.0) {
            entity.hurtServer(serverLevel, damageSource, damage);
            hitSet.add(entity);
        }
    }

    private static double getIntersectionT(Vec3 start, Vec3 end, AABB aabb) {
        return getIntersectionTPrimitive(start.x, start.y, start.z, end.x, end.y, end.z, aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ);
    }

    private static double getIntersectionTPrimitive(double startX, double startY, double startZ, double endX, double endY, double endZ, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        var dirX = endX - startX;
        var dirY = endY - startY;
        var dirZ = endZ - startZ;

        var tMin = 0.0;
        var tMax = 1.0;

        if (Math.abs(dirX) < 1.0E-7) {
            if (startX < minX || startX > maxX) return Double.MAX_VALUE;
        } else {
            var invDir = 1.0 / dirX;
            var t1 = (minX - startX) * invDir;
            var t2 = (maxX - startX) * invDir;
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));
        }

        if (Math.abs(dirY) < 1.0E-7) {
            if (startY < minY || startY > maxY) return Double.MAX_VALUE;
        } else {
            var invDir = 1.0 / dirY;
            var t1 = (minY - startY) * invDir;
            var t2 = (maxY - startY) * invDir;
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));
        }

        if (Math.abs(dirZ) < 1.0E-7) {
            if (startZ < minZ || startZ > maxZ) return Double.MAX_VALUE;
        } else {
            var invDir = 1.0 / dirZ;
            var t1 = (minZ - startZ) * invDir;
            var t2 = (maxZ - startZ) * invDir;
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));
        }

        if (tMin > tMax) {
            return Double.MAX_VALUE;
        }

        return tMin;
    }
}