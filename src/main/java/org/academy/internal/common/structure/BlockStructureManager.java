package org.academy.internal.common.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.BlockStructure;
import org.academy.api.common.structure.BlockStructureCaptureOptions;
import org.academy.api.common.structure.BlockStructureCaptureResult;
import org.academy.api.common.structure.BlockStructureGridAlignment;
import org.academy.api.common.structure.BlockStructurePlacementPolicy;
import org.academy.api.common.structure.BlockStructureRestoreResult;
import org.academy.api.common.structure.BlockStructureSnapshot;
import org.academy.internal.common.world.entity.structure.BlockStructureEntity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static net.minecraft.world.level.block.Block.UPDATE_CLIENTS;
import static net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE;
import static net.minecraft.world.level.block.Block.UPDATE_SUPPRESS_DROPS;

/** Server-authoritative capture and restoration transactions. */
public final class BlockStructureManager {
    private static final int BULK_UPDATE_FLAGS = UPDATE_CLIENTS
            | UPDATE_KNOWN_SHAPE
            | UPDATE_SUPPRESS_DROPS;

    private BlockStructureManager() {
    }

    public static BlockStructureCaptureResult captureConnected(
            ServerLevel level,
            BlockPos seed,
            BlockStructureCaptureOptions options
    ) {
        if (level == null || seed == null || options == null) {
            throw new IllegalArgumentException("level, seed, and options cannot be null");
        }
        var selected = new LinkedHashSet<BlockPos>();
        var visited = new HashSet<BlockPos>();
        var frontier = new ArrayDeque<BlockPos>();
        frontier.add(seed.immutable());
        while (!frontier.isEmpty()) {
            var position = frontier.removeFirst();
            if (!visited.add(position)) continue;
            if (!level.isInWorldBounds(position)) {
                return BlockStructureCaptureResult.failure(
                        BlockStructureCaptureResult.Status.OUT_OF_WORLD, position);
            }
            if (!level.isLoaded(position)) {
                return BlockStructureCaptureResult.failure(
                        BlockStructureCaptureResult.Status.UNLOADED, position);
            }
            var state = level.getBlockState(position);
            var blockEntity = level.getBlockEntity(position);
            if (!options.policy().canCapture(level, position, state, blockEntity)) continue;
            if (selected.size() >= options.maximumBlocks()) {
                return BlockStructureCaptureResult.failure(
                        BlockStructureCaptureResult.Status.TOO_LARGE, position);
            }
            selected.add(position);
            for (var direction : Direction.values()) {
                var adjacent = position.relative(direction);
                if (!visited.contains(adjacent)) frontier.addLast(adjacent.immutable());
            }
        }
        return capture(level, selected, options);
    }

    public static BlockStructureCaptureResult capture(
            ServerLevel level,
            Iterable<BlockPos> requestedPositions,
            BlockStructureCaptureOptions options
    ) {
        if (level == null || requestedPositions == null || options == null) {
            throw new IllegalArgumentException("Capture arguments cannot be null");
        }
        var positions = normalizedPositions(requestedPositions);
        if (positions.isEmpty()) {
            return BlockStructureCaptureResult.failure(
                    BlockStructureCaptureResult.Status.EMPTY, null);
        }
        if (positions.size() > options.maximumBlocks()) {
            return BlockStructureCaptureResult.failure(
                    BlockStructureCaptureResult.Status.TOO_LARGE, positions.get(options.maximumBlocks()));
        }

        var origin = minimumCorner(positions);
        var blocks = new ArrayList<BlockStructureSnapshot.BlockData>(positions.size());
        var blockEntityBytes = 0L;
        for (var position : positions) {
            if (!level.isInWorldBounds(position)) {
                return BlockStructureCaptureResult.failure(
                        BlockStructureCaptureResult.Status.OUT_OF_WORLD, position);
            }
            if (!level.isLoaded(position)) {
                return BlockStructureCaptureResult.failure(
                        BlockStructureCaptureResult.Status.UNLOADED, position);
            }
            var state = level.getBlockState(position);
            var blockEntity = level.getBlockEntity(position);
            if (!options.policy().canCapture(level, position, state, blockEntity)) {
                return BlockStructureCaptureResult.failure(
                        BlockStructureCaptureResult.Status.REJECTED, position);
            }
            CompoundTag blockEntityData = null;
            if (blockEntity != null) {
                blockEntityData = blockEntity.saveWithFullMetadata(level.registryAccess());
                blockEntityBytes += blockEntityData.sizeInBytes();
                if (blockEntityBytes > options.maximumBlockEntityBytes()) {
                    return BlockStructureCaptureResult.failure(
                            BlockStructureCaptureResult.Status.BLOCK_ENTITY_DATA_TOO_LARGE,
                            position
                    );
                }
            }
            blocks.add(new BlockStructureSnapshot.BlockData(
                    position.subtract(origin), state, blockEntityData));
        }
        var snapshot = new BlockStructureSnapshot(blocks);
        var removed = new ArrayList<BlockPos>(positions.size());
        for (var position : positions) {
            if (!level.setBlock(position, Blocks.AIR.defaultBlockState(), BULK_UPDATE_FLAGS)) {
                restoreOriginal(level, origin, snapshot, removed);
                return BlockStructureCaptureResult.failure(
                        BlockStructureCaptureResult.Status.WORLD_CHANGE_FAILED, position);
            }
            removed.add(position);
        }

        var spawned = spawn(
                level,
                snapshot,
                new Vec3(origin.getX(), origin.getY(), origin.getZ()),
                options.gravityEnabled(),
                options.restoreWhenSettled()
        );
        if (spawned.isEmpty()) {
            restoreOriginal(level, origin, snapshot, positions);
            return BlockStructureCaptureResult.failure(
                    BlockStructureCaptureResult.Status.ENTITY_SPAWN_FAILED, origin);
        }
        updateBoundaries(level, positions);
        return BlockStructureCaptureResult.success(spawned.get(), positions.size());
    }

    public static Optional<BlockStructure> spawn(
            ServerLevel level,
            BlockStructureSnapshot snapshot,
            Vec3 position,
            boolean gravityEnabled,
            boolean restoreWhenSettled
    ) {
        if (level == null || snapshot == null || snapshot.isEmpty() || !finite(position)) {
            return Optional.empty();
        }
        var blockPosition = BlockPos.containing(position);
        if (!level.isInWorldBounds(blockPosition) || !level.isLoaded(blockPosition)) {
            return Optional.empty();
        }
        var entity = new BlockStructureEntity(level);
        entity.initialize(snapshot, gravityEnabled, restoreWhenSettled);
        entity.setPos(position);
        return level.addFreshEntity(entity) ? Optional.of(entity) : Optional.empty();
    }

    public static BlockStructureRestoreResult restore(
            BlockStructureEntity entity,
            BlockStructurePlacementPolicy placementPolicy
    ) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return BlockStructureRestoreResult.failure(
                    BlockStructureRestoreResult.Status.WRONG_LEVEL, null);
        }
        var snapshot = entity.snapshot();
        if (snapshot.isEmpty()) {
            return BlockStructureRestoreResult.failure(
                    BlockStructureRestoreResult.Status.INVALID_STRUCTURE, null);
        }
        if (placementPolicy == null) throw new IllegalArgumentException("placementPolicy cannot be null");
        var alignment = BlockStructureGridAlignment.nearest(
                snapshot, entity.position(), entity.getYRot());
        var placements = new ArrayList<Placement>(snapshot.blockCount());
        var occupiedTargets = new HashSet<BlockPos>();
        for (var block : snapshot.blocks()) {
            var target = alignment.target(block.relativePosition());
            if (!occupiedTargets.add(target)) {
                return BlockStructureRestoreResult.failure(
                        BlockStructureRestoreResult.Status.INVALID_STRUCTURE, target);
            }
            if (!level.isInWorldBounds(target)) {
                return BlockStructureRestoreResult.failure(
                        BlockStructureRestoreResult.Status.OUT_OF_WORLD, target);
            }
            if (!level.isLoaded(target)) {
                return BlockStructureRestoreResult.failure(
                        BlockStructureRestoreResult.Status.UNLOADED, target);
            }
            var existing = level.getBlockState(target);
            if (!placementPolicy.canReplace(level, target, existing)) {
                return BlockStructureRestoreResult.failure(
                        BlockStructureRestoreResult.Status.OCCUPIED, target);
            }
            placements.add(new Placement(
                    target,
                    block.state().rotate(alignment.rotation()),
                    block.blockEntityData().orElse(null),
                    existing,
                    saveBlockEntity(level.getBlockEntity(target), level)
            ));
        }
        placements.sort(Comparator.comparingInt(value -> value.position.getY()));
        var placed = new ArrayList<Placement>(placements.size());
        for (var placement : placements) {
            if (!level.setBlock(placement.position, placement.state, BULK_UPDATE_FLAGS)) {
                rollbackPlacements(level, placed);
                return BlockStructureRestoreResult.failure(
                        BlockStructureRestoreResult.Status.WORLD_CHANGE_FAILED,
                        placement.position
                );
            }
            placed.add(placement);
        }
        for (var placement : placements) {
            if (placement.blockEntityData == null) continue;
            var blockEntity = BlockEntity.loadStatic(
                    placement.position,
                    placement.state,
                    placement.blockEntityData,
                    level.registryAccess()
            );
            if (blockEntity == null) {
                rollbackPlacements(level, placed);
                return BlockStructureRestoreResult.failure(
                        BlockStructureRestoreResult.Status.WORLD_CHANGE_FAILED,
                        placement.position
                );
            }
            level.setBlockEntity(blockEntity);
        }
        updateBoundaries(level, placements.stream().map(value -> value.position).toList());
        entity.discard();
        return BlockStructureRestoreResult.success(placements.size());
    }

    private static List<BlockPos> normalizedPositions(Iterable<BlockPos> positions) {
        var unique = new HashSet<BlockPos>();
        for (var position : positions) {
            if (position != null) unique.add(position.immutable());
        }
        var sorted = new ArrayList<>(unique);
        sorted.sort(Comparator.comparingInt((BlockPos position) -> position.getY())
                .thenComparingInt(position -> position.getX())
                .thenComparingInt(position -> position.getZ()));
        return sorted;
    }

    private static BlockPos minimumCorner(Collection<BlockPos> positions) {
        var minimumX = Integer.MAX_VALUE;
        var minimumY = Integer.MAX_VALUE;
        var minimumZ = Integer.MAX_VALUE;
        for (var position : positions) {
            minimumX = Math.min(minimumX, position.getX());
            minimumY = Math.min(minimumY, position.getY());
            minimumZ = Math.min(minimumZ, position.getZ());
        }
        return new BlockPos(minimumX, minimumY, minimumZ);
    }

    private static void restoreOriginal(
            ServerLevel level,
            BlockPos origin,
            BlockStructureSnapshot snapshot,
            Collection<BlockPos> removed
    ) {
        var removedSet = new HashSet<>(removed);
        for (var block : snapshot.blocks()) {
            var position = origin.offset(block.relativePosition());
            if (!removedSet.contains(position)) continue;
            level.setBlock(position, block.state(), BULK_UPDATE_FLAGS);
            block.blockEntityData().ifPresent(data -> {
                var blockEntity = BlockEntity.loadStatic(
                        position, block.state(), data, level.registryAccess());
                if (blockEntity != null) level.setBlockEntity(blockEntity);
            });
        }
        updateBoundaries(level, removed);
    }

    private static void rollbackPlacements(ServerLevel level, List<Placement> placements) {
        for (var index = placements.size() - 1; index >= 0; index--) {
            var placement = placements.get(index);
            level.setBlock(placement.position, placement.previousState, BULK_UPDATE_FLAGS);
            if (placement.previousBlockEntityData != null) {
                var blockEntity = BlockEntity.loadStatic(
                        placement.position,
                        placement.previousState,
                        placement.previousBlockEntityData,
                        level.registryAccess()
                );
                if (blockEntity != null) level.setBlockEntity(blockEntity);
            }
        }
        updateBoundaries(level, placements.stream().map(value -> value.position).toList());
    }

    private static CompoundTag saveBlockEntity(BlockEntity blockEntity, ServerLevel level) {
        return blockEntity == null ? null
                : blockEntity.saveWithFullMetadata(level.registryAccess());
    }

    private static boolean finite(Vec3 value) {
        return value != null
                && Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }

    private static void updateBoundaries(ServerLevel level, Collection<BlockPos> positions) {
        var updated = new HashSet<BlockPos>();
        for (var position : positions) {
            if (updated.add(position)) {
                level.updateNeighborsAt(position, level.getBlockState(position).getBlock());
            }
            for (var direction : Direction.values()) {
                var adjacent = position.relative(direction);
                if (updated.add(adjacent) && level.isLoaded(adjacent)) {
                    level.updateNeighborsAt(adjacent, level.getBlockState(adjacent).getBlock());
                }
            }
        }
    }

    private record Placement(
            BlockPos position,
            BlockState state,
            CompoundTag blockEntityData,
            BlockState previousState,
            CompoundTag previousBlockEntityData
    ) {
    }
}
