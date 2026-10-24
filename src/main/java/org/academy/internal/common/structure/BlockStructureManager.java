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
import org.academy.AcademyCraft;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.function.Predicate;

import static net.minecraft.world.level.block.Block.UPDATE_CLIENTS;
import static net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE;
import static net.minecraft.world.level.block.Block.UPDATE_SUPPRESS_DROPS;
import static net.minecraft.world.level.block.Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;
import static net.minecraft.world.level.block.Block.UPDATE_SKIP_ON_PLACE;

/** Server-authoritative capture and restoration transactions. */
public final class BlockStructureManager {
    private static final int BULK_UPDATE_FLAGS = UPDATE_CLIENTS
            | UPDATE_KNOWN_SHAPE
            | UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS
            | UPDATE_SKIP_ON_PLACE
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
        return selection.problemPosition()
                .map(position -> BlockStructureCaptureResult.failure(status, position))
                .orElseGet(() -> BlockStructureCaptureResult.failure(status));
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
        return cropImmediatelyBlocked(level, candidates, movementDirection, position -> false);
    }

    /** The optional pass-through policy only selects cells; it never grants or performs destruction. */
    public static List<BlockPos> cropImmediatelyBlocked(
            ServerLevel level,
            Iterable<BlockPos> candidates,
            Direction movementDirection,
            Predicate<BlockPos> passThrough
    ) {
        if (level == null || candidates == null || movementDirection == null || passThrough == null) {
            throw new IllegalArgumentException(
                    "level, candidates, and movementDirection cannot be null");
        }
        return cropImmediatelyBlocked(candidates, movementDirection, position -> {
            if (!level.isInWorldBounds(position) || !level.isLoaded(position)) return true;
            return !level.getBlockState(position).getCollisionShape(level, position).isEmpty()
                    && !passThrough.test(position);
        });
    }

    /**
     * Selection crop that keeps only full-block cells. Unloaded and out-of-world positions are
     * kept so the owning component can still surface them as explicit errors; this is a selection
     * policy, not a silent content drop.
     */
    public static List<BlockPos> cropNonFullBlocks(
            ServerLevel level,
            Iterable<BlockPos> candidates
    ) {
        if (level == null || candidates == null) {
            throw new IllegalArgumentException("level and candidates cannot be null");
        }
        return cropNonFullBlocks(candidates, position -> {
            if (!level.isInWorldBounds(position) || !level.isLoaded(position)) return true;
            return level.getBlockState(position).isCollisionShapeFullBlock(level, position);
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

    /**
     * Pure full-block crop: keeps candidates accepted by {@code fullBlock} in first-seen order
     * and drops duplicates. The predicate decides every edge case; this method never inspects
     * the world.
     */
    static List<BlockPos> cropNonFullBlocks(
            Iterable<BlockPos> candidates,
            Predicate<BlockPos> fullBlock
    ) {
        var kept = new LinkedHashSet<BlockPos>();
        for (var position : candidates) {
            if (position != null && fullBlock.test(position)) kept.add(position.immutable());
        }
        return List.copyOf(kept);
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
                    BlockStructureCaptureResult.Status.EMPTY);
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
            try {
                // Reject payloads we cannot faithfully reconstruct before admitting an entity or removing a source.
                BlockStructureTransferState.validateWrite(level, position, state, blockEntityData);
            } catch (RuntimeException failure) {
                AcademyCraft.LOGGER.warn("Block structure capture rejected non-restorable data at {}",
                        position, failure);
                return BlockStructureCaptureResult.failure(
                        BlockStructureCaptureResult.Status.REJECTED, position);
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
        var transfer = new BlockStructureTransferState(true);
        // Admit the entity before removing any source block, including cancellation by another mod.
        var spawned = spawn(
                level,
                snapshot,
                new Vec3(origin.getX(), origin.getY(), origin.getZ()),
                options.gravityEnabled(),
                options.restoreWhenSettled(),
                transfer
        );
        if (spawned.isEmpty()) {
            return BlockStructureCaptureResult.failure(
                    BlockStructureCaptureResult.Status.ENTITY_SPAWN_FAILED, origin);
        }
        var entity = (BlockStructureEntity) spawned.get();
        BlockPos changing = origin;
        try {
            for (var block : snapshot.blocks()) {
                var position = origin.offset(block.relativePosition());
                changing = position;
                requireUnchanged(level, position, block.state(), block.blockEntityData().orElse(null));
                if (entity.isRemoved()) throw new IllegalStateException("Capture entity was removed");
                transfer.writeCell(level, position, Blocks.AIR.defaultBlockState(),
                        null, BULK_UPDATE_FLAGS);
            }
            if (entity.isRemoved() || !transfer.matchesAfterImages(level)) {
                throw new IllegalStateException("Capture changed before ownership commit");
            }
        } catch (RuntimeException failure) {
            var recovered = rollbackTransfer(entity, level);
            AcademyCraft.LOGGER.error("Block structure {} capture failed at {}; rollback complete: {}",
                    entity.getUUID(), changing, recovered, failure);
            return recovered ? BlockStructureCaptureResult.failure(
                    BlockStructureCaptureResult.Status.WORLD_CHANGE_FAILED, changing)
                    : BlockStructureCaptureResult.failure(
                    BlockStructureCaptureResult.Status.RECOVERY_REQUIRED, changing);
        }
        entity.setPendingTransfer(null);
        for (var block : snapshot.blocks()) {
            notifyPlacement(level, origin.offset(block.relativePosition()),
                    Blocks.AIR.defaultBlockState(), block.state());
        }
        notifyBoundaries(level, positions);
        return BlockStructureCaptureResult.success(entity, positions.size());
    }

    public static Optional<BlockStructure> spawn(
            ServerLevel level,
            BlockStructureSnapshot snapshot,
            Vec3 position,
            boolean gravityEnabled,
            boolean restoreWhenSettled
    ) {
        return spawn(level, snapshot, position, gravityEnabled, restoreWhenSettled, null);
    }

    private static Optional<BlockStructure> spawn(
            ServerLevel level, BlockStructureSnapshot snapshot, Vec3 position,
            boolean gravityEnabled, boolean restoreWhenSettled, BlockStructureTransferState transfer
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
        entity.setPendingTransfer(transfer);
        try {
            if (level.addFreshEntity(entity) && !entity.isRemoved()) return Optional.of(entity);
        } catch (RuntimeException failure) {
            AcademyCraft.LOGGER.error("Block structure admission failed at {}", position, failure);
        }
        entity.discard();
        return Optional.empty();
    }

    public static BlockStructureRestoreResult restore(
            BlockStructureEntity entity,
            BlockStructurePlacementPolicy placementPolicy
    ) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return BlockStructureRestoreResult.failure(
                    BlockStructureRestoreResult.Status.WRONG_LEVEL);
        }
        if (entity.isRemoved()) {
            return BlockStructureRestoreResult.failure(BlockStructureRestoreResult.Status.INVALID_STRUCTURE);
        }
        if (entity.pendingTransfer() != null) {
            return BlockStructureRestoreResult.failure(BlockStructureRestoreResult.Status.RECOVERY_REQUIRED);
        }
        var snapshot = entity.snapshot();
        if (snapshot.isEmpty()) {
            return BlockStructureRestoreResult.failure(
                    BlockStructureRestoreResult.Status.INVALID_STRUCTURE);
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
        var transfer = new BlockStructureTransferState(false);
        entity.setPendingTransfer(transfer);
        BlockPos changing = entity.blockPosition();
        try {
            for (var placement : placements) {
                changing = placement.position;
                requireUnchanged(level, changing, placement.previousState, placement.previousBlockEntityData);
                if (entity.isRemoved()) throw new IllegalStateException("Restoring entity was removed");
                transfer.writeCell(level, changing, placement.state,
                        placement.blockEntityData, BULK_UPDATE_FLAGS);
            }
            if (entity.isRemoved() || !transfer.matchesAfterImages(level)) {
                throw new IllegalStateException("Restoration changed before ownership commit");
            }
        } catch (RuntimeException failure) {
            var recovered = rollbackTransfer(entity, level);
            AcademyCraft.LOGGER.error("Block structure restoration failed at {}; rollback complete: {}",
                    changing, recovered, failure);
            return BlockStructureRestoreResult.failure(recovered
                    ? BlockStructureRestoreResult.Status.WORLD_CHANGE_FAILED
                    : BlockStructureRestoreResult.Status.RECOVERY_REQUIRED, changing);
        }
        entity.setPendingTransfer(null);
        BlockStructureKineticRuntime.stop(entity);
        entity.discard();
        for (var placement : placements) {
            notifyPlacement(level, placement.position, placement.state, placement.previousState);
        }
        notifyBoundaries(level, placements.stream().map(value -> value.position).toList());
        return BlockStructureRestoreResult.success(placements.size());
    }

    /** All-or-nothing placement; failed settlement must not turn a container into an empty item. */
    public static BlockStructureSettlementResult settle(
            BlockStructureEntity entity,
            BlockStructurePlacementPolicy placementPolicy
    ) {
        var result = restore(entity, placementPolicy);
        if (result.succeeded()) return BlockStructureSettlementResult.success(result.affectedBlocks(), 0);
        entity.deferSettlement();
        var status = switch (result.status()) {
            case WRONG_LEVEL -> BlockStructureSettlementResult.Status.WRONG_LEVEL;
            case INVALID_STRUCTURE -> BlockStructureSettlementResult.Status.INVALID_STRUCTURE;
            case RECOVERY_REQUIRED -> BlockStructureSettlementResult.Status.RECOVERY_REQUIRED;
            default -> BlockStructureSettlementResult.Status.RETAINED;
        };
        return BlockStructureSettlementResult.failure(status);
    }

    /** Admit all falling cells and place all fixed cells before relinquishing the source payload. */
    public static void beginGravitySettlement(BlockStructureEntity entity) {
        if (entity == null || entity.isRemoved() || entity.isTransferPending()
                || !(entity.level() instanceof ServerLevel level) || entity.snapshot().isEmpty()) return;
        entity.deferSettlement();
        var alignment = BlockStructureGridAlignment.nearest(
                entity.snapshot(), entity.position(), entity.getYRot());
        var cells = entity.snapshot().blocks().stream()
                .map(block -> new SettlementPlacement(alignment.target(block.relativePosition()),
                        block.state().rotate(alignment.rotation()), block.blockEntityData().orElse(null),
                        block.settlementMode()))
                .sorted((left, right) -> BlockStructureSettlementMotion.compareBottomUp(
                        left.position, right.position))
                .toList();
        var targets = new HashSet<BlockPos>();
        for (var cell : cells) {
            if (!targets.add(cell.position) || !level.isInWorldBounds(cell.position)
                    || !level.isLoaded(cell.position) || !level.getBlockState(cell.position).isAir()) return;
        }
        var transfer = new BlockStructureTransferState(false);
        entity.setPendingTransfer(transfer);
        var fallingCells = new ArrayList<FallingBlockEntity>();
        var changed = new ArrayList<BlockPos>();
        try {
            for (var cell : cells) {
                if (!falls(cell)) continue;
                // Constructing the candidate does not mutate the block grid.
                var state = cell.state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)
                        ? cell.state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, false)
                        : cell.state;
                var falling = org.academy.mixin.common.FallingBlockEntityInvoker.academy$create(
                        level, cell.position.getX() + 0.5, cell.position.getY(), cell.position.getZ() + 0.5, state);
                fallingCells.add(falling);
                falling.setNoGravity(true);
                if (!level.addFreshEntity(falling) || falling.isRemoved() || falling.level() != level) {
                    throw new IllegalStateException("Falling cell admission rejected at " + cell.position);
                }
            }
            // Entity admission callbacks may have changed a destination since preflight.
            for (var cell : cells) requireUnchanged(level, cell.position, Blocks.AIR.defaultBlockState(), null);
            for (var cell : cells) {
                if (falls(cell)) continue;
                requireUnchanged(level, cell.position, Blocks.AIR.defaultBlockState(), null);
                transfer.writeCell(level, cell.position, cell.state,
                        cell.blockEntityData, BULK_UPDATE_FLAGS);
                changed.add(cell.position);
            }
            // Fixed-cell callbacks may also have replaced a falling cell's previously empty destination.
            for (var cell : cells) {
                if (falls(cell)) requireUnchanged(level, cell.position, Blocks.AIR.defaultBlockState(), null);
            }
            if (!transfer.matchesAfterImages(level)) {
                throw new IllegalStateException("Settlement blocks changed before ownership commit");
            }
            if (entity.isRemoved() || fallingCells.stream().anyMatch(FallingBlockEntity::isRemoved)) {
                throw new IllegalStateException("Settlement participant was removed before commit");
            }
        } catch (RuntimeException failure) {
            fallingCells.forEach(FallingBlockEntity::discard);
            var recovered = rollbackTransfer(entity, level);
            AcademyCraft.LOGGER.error("Gravity settlement failed for {}; rollback complete: {}",
                    entity.getUUID(), recovered, failure);
            return;
        }
        entity.setPendingTransfer(null);
        entity.discard();
        fallingCells.forEach(falling -> falling.setNoGravity(false));
        for (var cell : cells) {
            if (!falls(cell)) notifyPlacement(level, cell.position, cell.state, Blocks.AIR.defaultBlockState());
        }
        notifyBoundaries(level, changed);
    }

    private static boolean falls(SettlementPlacement cell) {
        return cell.settlementMode == BlockStructureSettlementMode.FALLING
                && cell.blockEntityData == null && !cell.state.hasBlockEntity();
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

    private static boolean rollbackTransfer(BlockStructureEntity entity, ServerLevel level) {
        var transfer = entity.pendingTransfer();
        if (transfer == null) return true;
        if (!transfer.rollback(level, BULK_UPDATE_FLAGS)) {
            // The entity remains quarantined. Its full payload is never automatically reactivated.
            return false;
        }
        entity.setPendingTransfer(null);
        if (transfer.discardAfterRollback()) entity.discard();
        else entity.deferSettlement();
        return true;
    }

    private static void requireUnchanged(ServerLevel level, BlockPos position, BlockState state,
                                         CompoundTag blockEntityData) {
        if (!level.isLoaded(position) || !level.getBlockState(position).equals(state)
                || !Objects.equals(blockEntityData, saveBlockEntity(level.getBlockEntity(position), level))) {
            throw new IllegalStateException("Block changed during transfer at " + position);
        }
    }

    private static void notifyBoundaries(ServerLevel level, Collection<BlockPos> positions) {
        try {
            updateBoundaries(level, positions);
        } catch (RuntimeException failure) {
            // Ownership has committed. A failing neighbor must not cause the payload to be duplicated.
            AcademyCraft.LOGGER.error("Structure boundary notification failed after transfer commit", failure);
        }
    }

    /** Run non-reversible placement effects only after the source relinquishes its payload. */
    private static void notifyPlacement(ServerLevel level, BlockPos position,
                                        BlockState expectedState, BlockState previousState) {
        try {
            // An earlier callback may have consumed or replaced this cell (for example a golem pattern).
            if (expectedState.equals(previousState) || !level.isLoaded(position)
                    || !level.getBlockState(position).equals(expectedState)) return;
            expectedState.onPlace(level, position, previousState, false);
        } catch (RuntimeException failure) {
            // Spawning an entity or consuming neighboring blocks cannot be undone by cell before-images.
            AcademyCraft.LOGGER.error("Structure placement callback failed after transfer commit at {}",
                    position, failure);
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
