package org.academy.internal.common.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.BlockStructure;
import org.academy.api.common.structure.BlockStructureCaptureOptions;
import org.academy.api.common.structure.BlockStructureCaptureResult;
import org.academy.api.common.structure.BlockStructureGridAlignment;
import org.academy.api.common.structure.BlockStructurePlacementPolicy;
import org.academy.api.common.structure.BlockStructureRestoreResult;
import org.academy.api.common.structure.BlockStructureSelectionResult;
import org.academy.api.common.structure.BlockStructureSettlementResult;
import org.academy.api.common.structure.BlockStructureSettlementMode;
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
import java.util.function.Predicate;

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
        if (options == null) throw new IllegalArgumentException("options cannot be null");
        var selection = selectConnected(
                level, seed, options.maximumBlocks(), options.policy());
        if (selection.succeeded()) {
            return capture(level, selection.positions(), options);
        }
        var status = switch (selection.status()) {
            case EMPTY -> BlockStructureCaptureResult.Status.EMPTY;
            case TOO_LARGE -> BlockStructureCaptureResult.Status.TOO_LARGE;
            case UNLOADED -> BlockStructureCaptureResult.Status.UNLOADED;
            case OUT_OF_WORLD -> BlockStructureCaptureResult.Status.OUT_OF_WORLD;
            case SUCCESS -> throw new IllegalStateException("Successful selection has no positions");
        };
        return BlockStructureCaptureResult.failure(
                status, selection.problemPosition().orElse(null));
    }

    public static BlockStructureSelectionResult selectConnected(
            ServerLevel level,
            BlockPos seed,
            int maximumBlocks,
            BlockStructureCaptureOptions.CapturePolicy policy
    ) {
        if (level == null || seed == null || policy == null) {
            throw new IllegalArgumentException("level, seed, and policy cannot be null");
        }
        if (maximumBlocks < 1 || maximumBlocks > BlockStructureSnapshot.MAX_BLOCKS) {
            throw new IllegalArgumentException("maximumBlocks must be between 1 and "
                    + BlockStructureSnapshot.MAX_BLOCKS);
        }
        var selected = new LinkedHashSet<BlockPos>();
        var visited = new HashSet<BlockPos>();
        var frontier = new ArrayDeque<BlockPos>();
        frontier.add(seed.immutable());
        while (!frontier.isEmpty()) {
            var position = frontier.removeFirst();
            if (!visited.add(position)) continue;
            if (!level.isInWorldBounds(position)) {
                return BlockStructureSelectionResult.failure(
                        BlockStructureSelectionResult.Status.OUT_OF_WORLD, position);
            }
            if (!level.isLoaded(position)) {
                return BlockStructureSelectionResult.failure(
                        BlockStructureSelectionResult.Status.UNLOADED, position);
            }
            var state = level.getBlockState(position);
            var blockEntity = level.getBlockEntity(position);
            if (!policy.canCapture(level, position, state, blockEntity)) continue;
            if (selected.size() >= maximumBlocks) {
                return BlockStructureSelectionResult.failure(
                        BlockStructureSelectionResult.Status.TOO_LARGE, position);
            }
            selected.add(position);
            for (var direction : Direction.values()) {
                var adjacent = position.relative(direction);
                if (!visited.contains(adjacent)) frontier.addLast(adjacent.immutable());
            }
        }
        return selected.isEmpty()
                ? BlockStructureSelectionResult.failure(
                BlockStructureSelectionResult.Status.EMPTY, seed)
                : BlockStructureSelectionResult.success(List.copyOf(selected));
    }

    public static BlockStructureSelectionResult selectSphere(
            ServerLevel level,
            BlockPos center,
            double radius,
            BlockStructureCaptureOptions.CapturePolicy policy
    ) {
        if (level == null || center == null || policy == null) {
            throw new IllegalArgumentException("level, center, and policy cannot be null");
        }
        if (!Double.isFinite(radius) || radius < 0.5 || radius > 32.0) {
            throw new IllegalArgumentException("radius must be between 0.5 and 32");
        }
        var selected = new ArrayList<BlockPos>();
        for (var position : positionsInSphere(center, radius)) {
            if (!level.isInWorldBounds(position)) {
                return BlockStructureSelectionResult.failure(
                        BlockStructureSelectionResult.Status.OUT_OF_WORLD, position);
            }
            if (!level.isLoaded(position)) {
                return BlockStructureSelectionResult.failure(
                        BlockStructureSelectionResult.Status.UNLOADED, position);
            }
            var state = level.getBlockState(position);
            var blockEntity = level.getBlockEntity(position);
            if (!policy.canCapture(level, position, state, blockEntity)) continue;
            if (selected.size() >= BlockStructureSnapshot.MAX_BLOCKS) {
                return BlockStructureSelectionResult.failure(
                        BlockStructureSelectionResult.Status.TOO_LARGE, position);
            }
            selected.add(position.immutable());
        }
        return selected.isEmpty()
                ? BlockStructureSelectionResult.failure(
                BlockStructureSelectionResult.Status.EMPTY, center)
                : BlockStructureSelectionResult.success(List.copyOf(selected));
    }

    public static BlockStructureSelectionResult selectEllipsoid(
            ServerLevel level,
            BlockPos center,
            double xRadius,
            double yRadius,
            double zRadius,
            BlockStructureCaptureOptions.CapturePolicy policy
    ) {
        if (level == null || center == null || policy == null) {
            throw new IllegalArgumentException("level, center, and policy cannot be null");
        }
        validateEllipsoidRadius(xRadius, "xRadius");
        validateEllipsoidRadius(yRadius, "yRadius");
        validateEllipsoidRadius(zRadius, "zRadius");
        var selected = new ArrayList<BlockPos>();
        for (var position : positionsInEllipsoid(center, xRadius, yRadius, zRadius)) {
            if (!level.isInWorldBounds(position)) {
                return BlockStructureSelectionResult.failure(
                        BlockStructureSelectionResult.Status.OUT_OF_WORLD, position);
            }
            if (!level.isLoaded(position)) {
                return BlockStructureSelectionResult.failure(
                        BlockStructureSelectionResult.Status.UNLOADED, position);
            }
            var state = level.getBlockState(position);
            var blockEntity = level.getBlockEntity(position);
            if (!policy.canCapture(level, position, state, blockEntity)) continue;
            if (selected.size() >= BlockStructureSnapshot.MAX_BLOCKS) {
                return BlockStructureSelectionResult.failure(
                        BlockStructureSelectionResult.Status.TOO_LARGE, position);
            }
            selected.add(position.immutable());
        }
        return selected.isEmpty()
                ? BlockStructureSelectionResult.failure(
                BlockStructureSelectionResult.Status.EMPTY, center)
                : BlockStructureSelectionResult.success(List.copyOf(selected));
    }

    public static BlockStructureSelectionResult selectEllipsoidWithCubeCore(
            ServerLevel level,
            BlockPos center,
            double xRadius,
            double yRadius,
            double zRadius,
            int cubeRadius,
            BlockStructureCaptureOptions.CapturePolicy policy
    ) {
        if (level == null || center == null || policy == null) {
            throw new IllegalArgumentException("level, center, and policy cannot be null");
        }
        var selected = new ArrayList<BlockPos>();
        for (var position : positionsInEllipsoidWithCubeCore(
                center, xRadius, yRadius, zRadius, cubeRadius)) {
            if (!level.isInWorldBounds(position)) {
                return BlockStructureSelectionResult.failure(
                        BlockStructureSelectionResult.Status.OUT_OF_WORLD, position);
            }
            if (!level.isLoaded(position)) {
                return BlockStructureSelectionResult.failure(
                        BlockStructureSelectionResult.Status.UNLOADED, position);
            }
            var state = level.getBlockState(position);
            var blockEntity = level.getBlockEntity(position);
            if (!policy.canCapture(level, position, state, blockEntity)) continue;
            if (selected.size() >= BlockStructureSnapshot.MAX_BLOCKS) {
                return BlockStructureSelectionResult.failure(
                        BlockStructureSelectionResult.Status.TOO_LARGE, position);
            }
            selected.add(position.immutable());
        }
        return selected.isEmpty()
                ? BlockStructureSelectionResult.failure(
                BlockStructureSelectionResult.Status.EMPTY, center)
                : BlockStructureSelectionResult.success(List.copyOf(selected));
    }

    public static BlockStructureSelectionResult selectLowerEllipsoidWithUpperCylinder(
            ServerLevel level,
            BlockPos center,
            double xRadius,
            double yRadius,
            double zRadius,
            double cylinderRadius,
            int cylinderHeight,
            BlockStructureCaptureOptions.CapturePolicy policy
    ) {
        if (level == null || center == null || policy == null) {
            throw new IllegalArgumentException("level, center, and policy cannot be null");
        }
        var selected = new ArrayList<BlockPos>();
        for (var position : positionsInLowerEllipsoidWithUpperCylinder(
                center,
                xRadius,
                yRadius,
                zRadius,
                cylinderRadius,
                cylinderHeight
        )) {
            if (!level.isInWorldBounds(position)) {
                return BlockStructureSelectionResult.failure(
                        BlockStructureSelectionResult.Status.OUT_OF_WORLD, position);
            }
            if (!level.isLoaded(position)) {
                return BlockStructureSelectionResult.failure(
                        BlockStructureSelectionResult.Status.UNLOADED, position);
            }
            var state = level.getBlockState(position);
            var blockEntity = level.getBlockEntity(position);
            if (!policy.canCapture(level, position, state, blockEntity)) continue;
            if (selected.size() >= BlockStructureSnapshot.MAX_BLOCKS) {
                return BlockStructureSelectionResult.failure(
                        BlockStructureSelectionResult.Status.TOO_LARGE, position);
            }
            selected.add(position.immutable());
        }
        return selected.isEmpty()
                ? BlockStructureSelectionResult.failure(
                BlockStructureSelectionResult.Status.EMPTY, center)
                : BlockStructureSelectionResult.success(List.copyOf(selected));
    }

    public static List<BlockPos> cropImmediatelyBlocked(
            ServerLevel level,
            Iterable<BlockPos> candidates,
            Direction movementDirection
    ) {
        if (level == null || candidates == null || movementDirection == null) {
            throw new IllegalArgumentException(
                    "level, candidates, and movementDirection cannot be null");
        }
        return cropImmediatelyBlocked(candidates, movementDirection, position -> {
            if (!level.isInWorldBounds(position) || !level.isLoaded(position)) return true;
            return !level.getBlockState(position).isAir();
        });
    }

    static List<BlockPos> positionsInSphere(BlockPos center, double radius) {
        var extent = (int) Math.ceil(radius);
        var radiusSquared = radius * radius + 1.0e-9;
        var positions = new ArrayList<BlockPos>();
        for (var y = -extent; y <= extent; y++) {
            for (var x = -extent; x <= extent; x++) {
                for (var z = -extent; z <= extent; z++) {
                    if ((double) x * x + (double) y * y + (double) z * z
                            <= radiusSquared) {
                        positions.add(center.offset(x, y, z));
                    }
                }
            }
        }
        return List.copyOf(positions);
    }

    static List<BlockPos> positionsInEllipsoid(
            BlockPos center,
            double xRadius,
            double yRadius,
            double zRadius
    ) {
        if (center == null) throw new IllegalArgumentException("center cannot be null");
        validateEllipsoidRadius(xRadius, "xRadius");
        validateEllipsoidRadius(yRadius, "yRadius");
        validateEllipsoidRadius(zRadius, "zRadius");
        var xExtent = (int) Math.ceil(xRadius);
        var yExtent = (int) Math.ceil(yRadius);
        var zExtent = (int) Math.ceil(zRadius);
        var positions = new ArrayList<BlockPos>();
        for (var y = -yExtent; y <= yExtent; y++) {
            for (var x = -xExtent; x <= xExtent; x++) {
                for (var z = -zExtent; z <= zExtent; z++) {
                    var normalizedDistance = x * x / (xRadius * xRadius)
                            + y * y / (yRadius * yRadius)
                            + z * z / (zRadius * zRadius);
                    if (normalizedDistance <= 1.0 + 1.0e-9) {
                        positions.add(center.offset(x, y, z));
                    }
                }
            }
        }
        return List.copyOf(positions);
    }

    static List<BlockPos> positionsInEllipsoidWithCubeCore(
            BlockPos center,
            double xRadius,
            double yRadius,
            double zRadius,
            int cubeRadius
    ) {
        if (cubeRadius < 0 || cubeRadius > 32) {
            throw new IllegalArgumentException("cubeRadius must be between 0 and 32");
        }
        var positions = new LinkedHashSet<>(
                positionsInEllipsoid(center, xRadius, yRadius, zRadius));
        for (var y = -cubeRadius; y <= cubeRadius; y++) {
            for (var x = -cubeRadius; x <= cubeRadius; x++) {
                for (var z = -cubeRadius; z <= cubeRadius; z++) {
                    positions.add(center.offset(x, y, z));
                }
            }
        }
        return List.copyOf(positions);
    }

    static List<BlockPos> positionsInLowerEllipsoidWithUpperCylinder(
            BlockPos center,
            double xRadius,
            double yRadius,
            double zRadius,
            double cylinderRadius,
            int cylinderHeight
    ) {
        if (center == null) throw new IllegalArgumentException("center cannot be null");
        validateEllipsoidRadius(xRadius, "xRadius");
        validateEllipsoidRadius(yRadius, "yRadius");
        validateEllipsoidRadius(zRadius, "zRadius");
        validateEllipsoidRadius(cylinderRadius, "cylinderRadius");
        if (cylinderHeight < 1 || cylinderHeight > 32) {
            throw new IllegalArgumentException("cylinderHeight must be between 1 and 32");
        }
        var positions = new LinkedHashSet<BlockPos>();
        var lowerEllipsoid = positionsInEllipsoid(center, xRadius, yRadius, zRadius);
        for (var position : lowerEllipsoid) {
            if (position.getY() <= center.getY()) positions.add(position);
        }
        var extent = (int) Math.ceil(cylinderRadius);
        var radiusSquared = cylinderRadius * cylinderRadius + 1.0e-9;
        for (var y = 1; y <= cylinderHeight; y++) {
            for (var x = -extent; x <= extent; x++) {
                for (var z = -extent; z <= extent; z++) {
                    if ((double) x * x + (double) z * z <= radiusSquared) {
                        positions.add(center.offset(x, y, z));
                    }
                }
            }
        }
        return List.copyOf(positions);
    }

    private static void validateEllipsoidRadius(double radius, String name) {
        if (!Double.isFinite(radius) || radius < 0.5 || radius > 32.0) {
            throw new IllegalArgumentException(name + " must be between 0.5 and 32");
        }
    }

    static List<BlockPos> cropImmediatelyBlocked(
            Iterable<BlockPos> candidates,
            Direction movementDirection,
            Predicate<BlockPos> externalBlocker
    ) {
        var movable = new LinkedHashSet<BlockPos>();
        for (var position : candidates) {
            if (position != null) movable.add(position.immutable());
        }
        boolean changed;
        do {
            changed = movable.removeIf(position -> {
                var adjacent = position.relative(movementDirection);
                return !movable.contains(adjacent) && externalBlocker.test(adjacent);
            });
        } while (changed && !movable.isEmpty());
        return List.copyOf(movable);
    }

    public static List<BlockPos> cropToForwardHalfSpace(
            Iterable<BlockPos> candidates,
            Vec3 origin,
            Vec3 forward,
            double minimumForwardDistance
    ) {
        if (candidates == null || !finite(origin) || !finite(forward)
                || forward.lengthSqr() <= 1.0e-8
                || !Double.isFinite(minimumForwardDistance)
                || minimumForwardDistance < 0.0
                || minimumForwardDistance > 256.0) {
            throw new IllegalArgumentException("Invalid forward-half-space crop arguments");
        }
        var direction = forward.normalize();
        var cropped = new LinkedHashSet<BlockPos>();
        for (var candidate : candidates) {
            if (candidate == null) continue;
            var forwardDistance = Vec3.atCenterOf(candidate)
                    .subtract(origin)
                    .dot(direction);
            if (forwardDistance + 1.0e-9 >= minimumForwardDistance) {
                cropped.add(candidate.immutable());
            }
        }
        return List.copyOf(cropped);
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
        var collisionBoxCount = 0;
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
            var collisionBoxes = state.getCollisionShape(level, position).toAabbs();
            if (collisionBoxes.size() > BlockStructureSnapshot.MAX_COLLISION_BOXES_PER_BLOCK) {
                return BlockStructureCaptureResult.failure(
                        BlockStructureCaptureResult.Status.COLLISION_DATA_TOO_LARGE,
                        position
                );
            }
            collisionBoxCount += collisionBoxes.size();
            if (collisionBoxCount > BlockStructureSnapshot.MAX_COLLISION_BOXES) {
                return BlockStructureCaptureResult.failure(
                        BlockStructureCaptureResult.Status.COLLISION_DATA_TOO_LARGE,
                        position
                );
            }
            blocks.add(new BlockStructureSnapshot.BlockData(
                    position.subtract(origin),
                    state,
                    blockEntityData,
                    collisionBoxes,
                    options.settlementPolicy().settlementMode(
                            level,
                            position,
                            state,
                            blockEntity
                    )
            ));
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
        BlockStructureKineticRuntime.stop(entity);
        entity.discard();
        return BlockStructureRestoreResult.success(placements.size());
    }

    /**
     * Terminal, best-effort restoration used when a structure stops moving.
     * Unlike {@link #restore(BlockStructureEntity, BlockStructurePlacementPolicy)},
     * this operation cannot leave the entity behind: each cell is either placed
     * independently or emitted as a block item before the structure is discarded.
     */
    public static BlockStructureSettlementResult settle(
            BlockStructureEntity entity,
            BlockStructurePlacementPolicy placementPolicy
    ) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return BlockStructureSettlementResult.failure(
                    BlockStructureSettlementResult.Status.WRONG_LEVEL);
        }
        var snapshot = entity.snapshot();
        if (snapshot.isEmpty()) {
            BlockStructureKineticRuntime.stop(entity);
            entity.discard();
            return BlockStructureSettlementResult.failure(
                    BlockStructureSettlementResult.Status.INVALID_STRUCTURE);
        }
        if (placementPolicy == null) {
            throw new IllegalArgumentException("placementPolicy cannot be null");
        }

        var alignment = BlockStructureGridAlignment.nearest(
                snapshot, entity.position(), entity.getYRot());
        var placements = snapshot.blocks().stream()
                .map(block -> new SettlementPlacement(
                        alignment.target(block.relativePosition()),
                        block.state().rotate(alignment.rotation()),
                        block.blockEntityData().orElse(null),
                        block.settlementMode()))
                .sorted(Comparator.comparingInt(value -> value.position.getY()))
                .toList();
        var occupiedTargets = new HashSet<BlockPos>();
        var changedPositions = new ArrayList<BlockPos>();
        var dropPosition = entity.blockPosition().immutable();
        var placedBlocks = 0;
        var droppedBlocks = 0;

        for (var placement : placements) {
            if (settleCell(level, placement, placementPolicy,
                    occupiedTargets, changedPositions)) placedBlocks++;
            else {
                dropBlock(level, dropPosition, placement.state);
                droppedBlocks++;
            }
        }

        try {
            updateBoundaries(level, changedPositions);
        } finally {
            BlockStructureKineticRuntime.stop(entity);
            entity.discard();
        }
        return BlockStructureSettlementResult.success(placedBlocks, droppedBlocks);
    }

    /**
     * Dematerializes a stopped structure. Fixed building cells restore directly, while natural
     * terrain cells become vanilla falling-block entities from the lowest layer upward. Cells
     * that cannot be emitted safely become block-item drops instead.
     */
    public static void beginGravitySettlement(BlockStructureEntity entity) {
        if (entity == null || entity.isRemoved()) return;
        if (!(entity.level() instanceof ServerLevel level)) {
            entity.discard();
            return;
        }
        var snapshot = entity.snapshot();
        if (snapshot.isEmpty()) {
            BlockStructureKineticRuntime.stop(entity);
            entity.discard();
            return;
        }
        var alignment = BlockStructureGridAlignment.nearest(
                snapshot, entity.position(), entity.getYRot());
        var cells = snapshot.blocks().stream()
                .map(block -> new SettlementPlacement(
                        alignment.target(block.relativePosition()),
                        block.state().rotate(alignment.rotation()),
                        block.blockEntityData().orElse(null),
                        block.settlementMode()))
                .sorted((left, right) -> BlockStructureSettlementMotion.compareBottomUp(
                        left.position, right.position))
                .toList();
        var dropPosition = entity.blockPosition().immutable();
        BlockStructureKineticRuntime.stop(entity);
        entity.discard();

        var occupiedTargets = new HashSet<BlockPos>();
        var changedPositions = new ArrayList<BlockPos>();
        for (var cell : cells) {
            if (cell.settlementMode != BlockStructureSettlementMode.FIXED) continue;
            if (!settleCell(
                    level,
                    cell,
                    BlockStructurePlacementPolicy.AIR_ONLY,
                    occupiedTargets,
                    changedPositions
            )) {
                dropBlock(level, dropPosition, cell.state);
            }
        }
        updateBoundaries(level, changedPositions);
        for (var cell : cells) {
            if (cell.settlementMode != BlockStructureSettlementMode.FALLING) continue;
            if (!occupiedTargets.add(cell.position)
                    || !level.isInWorldBounds(cell.position)
                    || !level.isLoaded(cell.position)
                    || !level.getBlockState(cell.position).isAir()
                    || !spawnFallingCell(level, cell)) {
                dropBlock(level, dropPosition, cell.state);
            }
        }
    }

    private static boolean spawnFallingCell(
            ServerLevel level,
            SettlementPlacement cell
    ) {
        try {
            if (!level.setBlock(cell.position, cell.state, BULK_UPDATE_FLAGS)) return false;
            var falling = FallingBlockEntity.fall(level, cell.position, cell.state);
            falling.dropItem = true;
            if (cell.blockEntityData != null) {
                falling.blockData = cell.blockEntityData.copy();
            }
            return true;
        } catch (RuntimeException ignored) {
            if (level.isLoaded(cell.position)
                    && level.getBlockState(cell.position).equals(cell.state)) {
                level.setBlock(cell.position, Blocks.AIR.defaultBlockState(), BULK_UPDATE_FLAGS);
            }
            return false;
        }
    }

    private static boolean settleCell(
            ServerLevel level,
            SettlementPlacement placement,
            BlockStructurePlacementPolicy placementPolicy,
            HashSet<BlockPos> occupiedTargets,
            ArrayList<BlockPos> changedPositions
    ) {
        if (!occupiedTargets.add(placement.position)
                || !level.isInWorldBounds(placement.position)
                || !level.isLoaded(placement.position)) return false;
        BlockState previousState = null;
        CompoundTag previousBlockEntityData = null;
        var changed = false;
        try {
            previousState = level.getBlockState(placement.position);
            if (!placementPolicy.canReplace(level, placement.position, previousState)) return false;
            previousBlockEntityData = saveBlockEntity(
                    level.getBlockEntity(placement.position), level);
            if (!level.setBlock(placement.position, placement.state, BULK_UPDATE_FLAGS)) {
                return false;
            }
            changed = true;
            changedPositions.add(placement.position);
            if (placement.blockEntityData == null) return true;
            var blockEntity = BlockEntity.loadStatic(
                    placement.position,
                    placement.state,
                    placement.blockEntityData,
                    level.registryAccess()
            );
            if (blockEntity == null) {
                restoreCell(level, placement.position, previousState,
                        previousBlockEntityData);
                return false;
            }
            level.setBlockEntity(blockEntity);
            blockEntity.setChanged();
            return true;
        } catch (RuntimeException ignored) {
            if (changed && previousState != null) {
                try {
                    restoreCell(level, placement.position, previousState,
                            previousBlockEntityData);
                } catch (RuntimeException ignoredRestoreFailure) {
                    // The entity still terminates; this cell is represented by its dropped item.
                }
            }
            return false;
        }
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

    private static void restoreCell(
            ServerLevel level,
            BlockPos position,
            BlockState previousState,
            CompoundTag previousBlockEntityData
    ) {
        level.setBlock(position, previousState, BULK_UPDATE_FLAGS);
        if (previousBlockEntityData == null) return;
        var blockEntity = BlockEntity.loadStatic(
                position,
                previousState,
                previousBlockEntityData,
                level.registryAccess()
        );
        if (blockEntity != null) level.setBlockEntity(blockEntity);
    }

    private static void dropBlock(
            ServerLevel level,
            BlockPos dropPosition,
            BlockState state
    ) {
        try {
            var stack = new ItemStack(state.getBlock());
            if (!stack.isEmpty()) Block.popResource(level, dropPosition, stack);
        } catch (RuntimeException ignored) {
            // A third-party block must not keep the structure entity alive forever.
        }
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

    private record SettlementPlacement(
            BlockPos position,
            BlockState state,
            CompoundTag blockEntityData,
            BlockStructureSettlementMode settlementMode
    ) {
    }
}
