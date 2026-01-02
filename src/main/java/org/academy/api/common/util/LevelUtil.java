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
    public static double getValidViewDistance(Entity entity, double targetDistance) {
        var startPos = entity.getEyePosition();
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

    // Context object to avoid passing 10+ arguments meow
    private record BlockCollectionContext(
            Level level, Vec3 start, Vec3 end, float radius, int miningLevel, boolean canBlock,
            List<BlockPos> breakable, List<BlockPos> unbreakable,
            LongOpenHashSet visited, BlockPos.MutableBlockPos mutablePos
    ) {}

    private static Pair<List<BlockPos>, List<BlockPos>> collectBlocksOptimized(Level level, Vec3 start, Vec3 end, float radius, int miningLevel, boolean canBlock) {
        var context = new BlockCollectionContext(
                level, start, end, radius, miningLevel, canBlock,
                new ArrayList<>(), new ArrayList<>(),
                new LongOpenHashSet(), new BlockPos.MutableBlockPos()
        );

        var dir = end.subtract(start).normalize();
        var length = start.distanceTo(end);
        // Step size heuristic: ensure we don't skip blocks meow
        var step = Math.max(0.5, radius * 0.8);

        ChunkAccess currentChunk = null;
        var currentChunkX = Integer.MAX_VALUE;
        var currentChunkZ = Integer.MAX_VALUE;

        for (double d = 0; d <= length; d += step) {
            var samplePoint = start.add(dir.scale(d));

            var minX = Mth.floor(samplePoint.x - radius);
            var maxX = Mth.floor(samplePoint.x + radius);
            var minZ = Mth.floor(samplePoint.z - radius);
            var maxZ = Mth.floor(samplePoint.z + radius);

            for (var x = minX; x <= maxX; x++) {
                for (var z = minZ; z <= maxZ; z++) {
                    var chunkX = SectionPos.blockToSectionCoord(x);
                    var chunkZ = SectionPos.blockToSectionCoord(z);

                    // Cache chunk lookup to avoid repeated map access meow
                    if (chunkX != currentChunkX || chunkZ != currentChunkZ) {
                        currentChunk = level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                        currentChunkX = chunkX;
                        currentChunkZ = chunkZ;
                    }

                    if (currentChunk == null) continue;

                    var minY = Mth.floor(samplePoint.y - radius);
                    var maxY = Mth.floor(samplePoint.y + radius);

                    processChunkColumn(context, currentChunk, x, z, minY, maxY);
                }
            }
        }
        return Pair.of(context.breakable, context.unbreakable);
    }

    private static void processChunkColumn(BlockCollectionContext ctx, ChunkAccess chunk, int x, int z, int minY, int maxY) {
        for (var y = minY; y <= maxY; y++) {
            var posPacked = BlockPos.asLong(x, y, z);
            if (!ctx.visited.add(posPacked)) continue;

            var blockState = getBlockStateFast(chunk, ctx.level, x, y, z);
            if (blockState.isAir()) continue;

            checkAndCollectBlock(ctx, blockState, x, y, z);
        }
    }

    private static void checkAndCollectBlock(BlockCollectionContext ctx, BlockState state, int x, int y, int z) {
        ctx.mutablePos.set(x, y, z);
        var shape = state.getCollisionShape(ctx.level, ctx.mutablePos);
        if (shape.isEmpty()) return;

        var blockAABB = shape.bounds().move(x, y, z);
        // Sphere-AABB intersection check meow
        if (getIntersectionT(ctx.start, ctx.end, blockAABB.inflate(ctx.radius)) <= 1.0) {
            if (canBreakBlock(state, ctx.miningLevel)) {
                ctx.breakable.add(ctx.mutablePos.immutable());
            } else if (ctx.canBlock) {
                ctx.unbreakable.add(ctx.mutablePos.immutable());
            }
        }
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
                // Capture BE before setting block to air meow
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

    /**
     * Slab method for Ray-AABB intersection.
     * Calculates the entry time 't' along the ray direction.
     */
    private static double getIntersectionTPrimitive(double startX, double startY, double startZ, double endX, double endY, double endZ, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        var dirX = endX - startX;
        var dirY = endY - startY;
        var dirZ = endZ - startZ;

        var tMin = 0.0;
        var tMax = 1.0;

        // X Axis meow
        tMin = Math.max(tMin, getAxisEntryT(startX, dirX, minX, maxX));
        tMax = Math.min(tMax, getAxisExitT(startX, dirX, minX, maxX));

        // Y Axis meow
        tMin = Math.max(tMin, getAxisEntryT(startY, dirY, minY, maxY));
        tMax = Math.min(tMax, getAxisExitT(startY, dirY, minY, maxY));

        // Z Axis meow
        tMin = Math.max(tMin, getAxisEntryT(startZ, dirZ, minZ, maxZ));
        tMax = Math.min(tMax, getAxisExitT(startZ, dirZ, minZ, maxZ));

        if (tMin > tMax) return Double.MAX_VALUE;

        return tMin;
    }

    private static double getAxisEntryT(double start, double dir, double min, double max) {
        if (Math.abs(dir) < 1.0E-7)
            // Parallel and outside: Entry is +Infinity (forces failure)
            // Parallel and inside: Entry is -Infinity (no constraint)
            return (start < min || start > max) ? Double.MAX_VALUE : -Double.MAX_VALUE;
        var invDir = 1.0 / dir;
        return Math.min((min - start) * invDir, (max - start) * invDir);
    }

    private static double getAxisExitT(double start, double dir, double min, double max) {
        if (Math.abs(dir) < 1.0E-7)
            // Parallel and outside: Exit is -Infinity (forces failure)
            // Parallel and inside: Exit is +Infinity (no constraint)
            return (start < min || start > max) ? -Double.MAX_VALUE : Double.MAX_VALUE;
        var invDir = 1.0 / dir;
        return Math.max((min - start) * invDir, (max - start) * invDir);
    }
}