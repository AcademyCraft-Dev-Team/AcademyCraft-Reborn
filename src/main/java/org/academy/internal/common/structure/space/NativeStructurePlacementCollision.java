package org.academy.internal.common.structure.space;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.academy.api.common.structure.BlockStructureCollision;
import org.academy.api.common.structure.StructureContexts;
import org.academy.api.common.structure.StructureHandle;
import org.academy.api.common.structure.StructureLifecycle;
import org.academy.api.common.structure.StructurePose;
import org.academy.internal.common.structure.BlockStructureCollisionRuntime;
import org.academy.internal.common.structure.BlockStructureOrientedBox;
import org.academy.internal.common.structure.NativeStructureRuntime;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Bounded placement overlap checks; never loads a missing host/storage chunk. */
public final class NativeStructurePlacementCollision {
    private static final int MAX_BLOCK_VISITS = 4_096;
    private static final int MAX_STATE_READS = 8_192;
    private static final int MAX_SHAPE_BOXES = 64;
    private static final int MAX_PAIR_TESTS = 32_768;
    private static final int MAX_ENTITIES = 256;
    private static final double WORLD_LIMIT = 30_000_000;

    private NativeStructurePlacementCollision() {}

    /**
     * Checks the proposed collision shape before writing it. The caller still owns local
     * replacement/survival checks and must repeat this check after cancellable callbacks.
     * Like vanilla's block collision neighbourhood, shapes may extend one cell past their block.
     */
    public static boolean canPlace(ServerLevel host, StructureHandle handle, ServerLevel storage,
                                   BlockPos localPos, BlockState proposedState) {
        return preflight(host, handle, storage, localPos, proposedState) != null;
    }

    /** Captures geometry observations so the final write can verify them without invoking shapes again. */
    public static @Nullable Guard preflight(ServerLevel host, StructureHandle handle, ServerLevel storage,
                                             BlockPos localPos, BlockState proposedState) {
        Objects.requireNonNull(host);
        Objects.requireNonNull(handle);
        Objects.requireNonNull(storage);
        Objects.requireNonNull(localPos);
        Objects.requireNonNull(proposedState);
        if (!host.getServer().isSameThread()) throw new IllegalStateException("Placement collisions require the server thread");
        try {
            if (host.getServer().getLevel(host.dimension()) != host || storage.getServer() != host.getServer()
                    || NativeStructureDimensions.isStorageLevel(host.dimension())
                    || !NativeStructureDimensions.isStorageLevel(storage.dimension())
                    || !NativeStructureDimensions.contains(localPos) || storage.isOutsideBuildHeight(localPos)
                    || !handle.reference().dimension().equals(host.dimension().identifier())) return null;
            var runtime = NativeStructureRuntime.backend(host.getServer());
            if (runtime.resolve(handle.reference()).orElse(null) != handle
                    || runtime.storageLevel(handle.reference()).orElse(null) != storage) return null;
            var snapshots = new ArrayList<Snapshot>();
            for (var active : runtime.activeStructures(host)) {
                snapshots.add(new Snapshot(active, active.runtimeId(), active.geometryRevision(), active.pose(),
                        runtime.storageLevel(active.reference()).orElseThrow(Denied::new)));
            }
            var target = snapshots.stream().filter(snapshot -> snapshot.handle == handle).findFirst().orElseThrow(Denied::new);
            var budget = new Budget();
            var getter = new LoadedGetter(storage, true, budget, localPos.immutable(), proposedState);
            var collisionBoxes = StructureContexts.call(handle, ignored -> boxes(
                    proposedState.getCollisionShape(getter, localPos, CollisionContext.empty()), budget));
            var occupiedBoxes = collisionBoxes.isEmpty() ? StructureContexts.call(handle, ignored -> boxes(
                    proposedState.getShape(getter, localPos, CollisionContext.empty()), budget)) : collisionBoxes;
            var candidates = new ArrayList<BlockStructureOrientedBox>();
            var entityCandidates = new ArrayList<BlockStructureOrientedBox>();
            AABB worldBounds = BlockStructureOrientedBox.transform(new AABB(localPos), target.pose).bounds();
            for (var box : occupiedBoxes) {
                var candidate = BlockStructureOrientedBox.transform(box.move(localPos), target.pose);
                candidates.add(candidate);
                worldBounds = worldBounds.minmax(candidate.bounds());
            }
            for (var box : collisionBoxes) {
                entityCandidates.add(BlockStructureOrientedBox.transform(box.move(localPos), target.pose));
            }
            requireWorldBounds(host, worldBounds);
            requireLoadedChunks(host, worldBounds);
            if (!candidates.isEmpty()) {
                testBlocks(new LoadedGetter(host, false, budget, null, null),
                        StructurePose.identity(Vec3.ZERO), worldBounds, candidates, budget);
                testEntities(host, worldBounds, candidates, entityCandidates, budget);
                for (var snapshot : snapshots) {
                    if (snapshot.handle == handle) continue;
                    var bounds = inverseBounds(worldBounds, snapshot.pose);
                    StructureContexts.run(snapshot.handle, ignored -> testBlocks(
                            new LoadedGetter(snapshot.storage, true, budget, null, null),
                            snapshot.pose, bounds, candidates, budget));
                }
            }
            // Shape and entity callbacks may reenter mod code. Reject mixed observations,
            // including newly activated structures that were absent from the original scan.
            var guard = new Guard(host, worldBounds, runtime, snapshots, budget);
            return guard.stillValid() ? guard : null;
        } catch (Denied | IllegalStateException | IllegalArgumentException unavailable) {
            return null;
        }
    }

    /** Server-thread receipt. Its validation performs no collision/outline shape callbacks. */
    public static final class Guard {
        private final ServerLevel host;
        private final AABB bounds;
        private final NativeStructureRuntime runtime;
        private final List<Snapshot> snapshots;
        private final Budget observations;

        private Guard(ServerLevel host, AABB bounds, NativeStructureRuntime runtime,
                      List<Snapshot> snapshots, Budget observations) {
            this.host = host;
            this.bounds = bounds;
            this.runtime = runtime;
            this.snapshots = List.copyOf(snapshots);
            this.observations = observations;
        }

        public boolean stillValid() {
            if (!host.getServer().isSameThread()) throw new IllegalStateException("Placement collision receipts require the server thread");
            try {
                if (host.getServer().getLevel(host.dimension()) != host) return false;
                var current = runtime.activeStructures(host);
                if (current.size() != snapshots.size()) return false;
                for (var snapshot : snapshots) if (!snapshot.current() || !current.contains(snapshot.handle)) return false;
                requireWorldBounds(host, bounds);
                requireLoadedChunks(host, bounds);
                return observations.current();
            } catch (Denied | IllegalStateException | IllegalArgumentException unavailable) {
                return false;
            }
        }
    }

    private static void testBlocks(LoadedGetter getter, StructurePose pose, AABB queryBounds,
                                   List<BlockStructureOrientedBox> candidates, Budget budget) {
        var level = getter.level;
        // One-cell expansion includes fences, walls, pistons and other vanilla extended shapes.
        int minX = floor(queryBounds.minX) - 1;
        int minY = Math.max(level.getMinY(), floor(queryBounds.minY) - 1);
        int minZ = floor(queryBounds.minZ) - 1;
        int maxX = floor(queryBounds.maxX) + 1;
        int maxY = Math.min(level.getMaxY(), floor(queryBounds.maxY) + 1);
        int maxZ = floor(queryBounds.maxZ) + 1;
        if (getter.structure) {
            minX = Math.max(NativeStructureDimensions.MIN_COORDINATE, minX);
            minZ = Math.max(NativeStructureDimensions.MIN_COORDINATE, minZ);
            maxX = Math.min(NativeStructureDimensions.MAX_COORDINATE_EXCLUSIVE - 1, maxX);
            maxZ = Math.min(NativeStructureDimensions.MAX_COORDINATE_EXCLUSIVE - 1, maxZ);
        }
        if (minX > maxX || minY > maxY || minZ > maxZ) return;
        budget.visitBlocks((long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1));
        for (var pos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            var state = getter.getBlockState(pos);
            if (state.isAir()) continue;
            var shape = state.getCollisionShape(getter, pos, CollisionContext.empty());
            // Non-solid blocks still occupy their model space in a distinct level. In
            // particular, a lever may overlap an actor but cannot be embedded in host stone.
            if (shape.isEmpty()) shape = state.getShape(getter, pos, CollisionContext.empty());
            for (var box : boxes(shape, budget)) {
                testBox(BlockStructureOrientedBox.transform(box.move(pos), pose), candidates, budget);
            }
        }
    }

    private static void testEntities(ServerLevel host, AABB bounds,
                                     List<BlockStructureOrientedBox> occupiedCandidates,
                                     List<BlockStructureOrientedBox> entityCandidates, Budget budget) {
        var entities = entityCandidates(host, bounds);
        budget.observeEntityQuery(host, bounds, entities);
        for (var entity : entities) {
            budget.observeEntity(entity);
            if (entity instanceof BlockStructureCollision collision) {
                collision.collectCollisionShapes(bounds, shape -> {
                    budget.readShape();
                    shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
                        budget.testPair();
                        testBox(BlockStructureOrientedBox.axisAligned(new AABB(minX, minY, minZ, maxX, maxY, maxZ)), occupiedCandidates, budget);
                    });
                });
            } else {
                testBox(BlockStructureOrientedBox.axisAligned(entity.getBoundingBox()), entityCandidates, budget);
            }
        }
    }

    private static Set<Entity> entityCandidates(ServerLevel host, AABB bounds) {
        Set<Entity> entities = Collections.newSetFromMap(new IdentityHashMap<>());
        int[] visits = {0};
        entities.addAll(host.getEntities((Entity) null, bounds, entity -> {
            if (++visits[0] > MAX_ENTITIES) throw new Denied();
            return !entity.isRemoved() && !entity.isSpectator()
                    && (entity.blocksBuilding || entity instanceof BlockStructureCollision);
        }));
        for (var entity : BlockStructureCollisionRuntime.query(host, bounds)) {
            if (entities.add(entity) && ++visits[0] > MAX_ENTITIES) throw new Denied();
        }
        return entities;
    }

    private static void testBox(BlockStructureOrientedBox obstacle,
                                List<BlockStructureOrientedBox> candidates, Budget budget) {
        for (var candidate : candidates) {
            budget.testPair();
            if (candidate.intersects(obstacle)) throw new Denied();
        }
    }

    private static List<AABB> boxes(VoxelShape shape, Budget budget) {
        budget.readShape();
        var result = new ArrayList<AABB>();
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
            if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)
                    || !Double.isFinite(maxX) || !Double.isFinite(maxY) || !Double.isFinite(maxZ)
                    || result.size() >= MAX_SHAPE_BOXES || minX < -1 || minY < -1 || minZ < -1
                    || maxX > 2 || maxY > 2 || maxZ > 2) throw new Denied();
            if (maxX > minX && maxY > minY && maxZ > minZ) result.add(new AABB(minX, minY, minZ, maxX, maxY, maxZ));
        });
        return result;
    }

    private static AABB inverseBounds(AABB world, StructurePose pose) {
        AABB result = null;
        for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) for (int z = 0; z < 2; z++) {
            var point = pose.worldToLocalPoint(new Vec3(x == 0 ? world.minX : world.maxX,
                    y == 0 ? world.minY : world.maxY, z == 0 ? world.minZ : world.maxZ));
            var corner = new AABB(point, point);
            result = result == null ? corner : result.minmax(corner);
        }
        return Objects.requireNonNull(result);
    }

    private static void requireWorldBounds(ServerLevel host, AABB bounds) {
        if (bounds.minX < -WORLD_LIMIT || bounds.maxX > WORLD_LIMIT
                || bounds.minZ < -WORLD_LIMIT || bounds.maxZ > WORLD_LIMIT
                || bounds.minY < host.getMinY() || bounds.maxY > host.getMaxY() + 1.0
                || !host.getWorldBorder().isWithinBounds(bounds)) throw new Denied();
    }

    private static void requireLoadedChunks(ServerLevel level, AABB bounds) {
        for (int x = floor(bounds.minX) >> 4; x <= floor(bounds.maxX) >> 4; x++) {
            for (int z = floor(bounds.minZ) >> 4; z <= floor(bounds.maxZ) >> 4; z++) {
                if (!level.getChunkSource().hasChunk(x, z)) throw new Denied();
            }
        }
    }

    private static int floor(double value) {
        if (!Double.isFinite(value) || value < Integer.MIN_VALUE + 2.0 || value > Integer.MAX_VALUE - 2.0) throw new Denied();
        return (int) Math.floor(value);
    }

    private record Snapshot(StructureHandle handle, UUID runtimeId, long revision, StructurePose pose, ServerLevel storage) {
        private boolean current() {
            return handle.lifecycle() == StructureLifecycle.ACTIVE && handle.runtimeId().equals(runtimeId)
                    && handle.geometryRevision() == revision && handle.pose().equals(pose);
        }
    }

    private static final class Budget {
        private final Map<ServerLevel, Map<BlockPos, BlockState>> observations = new IdentityHashMap<>();
        private final Map<ServerLevel, Map<BlockPos, BlockEntity>> blockEntities = new IdentityHashMap<>();
        private final Map<Entity, AABB> entityBounds = new IdentityHashMap<>();
        private @Nullable ServerLevel entityHost;
        private @Nullable AABB entityQueryBounds;
        private Set<Entity> queriedEntities = Set.of();
        private long blockVisits;
        private int reads;
        private int shapes;
        private int pairs;
        private void visitBlocks(long count) { if (count < 0 || (blockVisits += count) > MAX_BLOCK_VISITS) throw new Denied(); }
        private void read() { if (++reads > MAX_STATE_READS) throw new Denied(); }
        private void readShape() { if (++shapes > MAX_BLOCK_VISITS) throw new Denied(); }
        private void testPair() { if (++pairs > MAX_PAIR_TESTS) throw new Denied(); }
        private BlockState observeState(ServerLevel level, BlockPos position) {
            var state = level.getBlockState(position);
            var previous = observations.computeIfAbsent(level, ignored -> new HashMap<>()).putIfAbsent(position.immutable(), state);
            if (previous != null && previous != state) throw new Denied();
            return state;
        }
        private void observeEntity(Entity entity) { entityBounds.put(entity, entity.getBoundingBox()); }
        private @Nullable BlockEntity observeBlockEntity(ServerLevel level, BlockPos position) {
            var entity = level.getBlockEntity(position);
            var known = blockEntities.computeIfAbsent(level, ignored -> new HashMap<>());
            if (known.containsKey(position) && known.get(position) != entity) throw new Denied();
            known.put(position.immutable(), entity);
            return entity;
        }
        private void observeEntityQuery(ServerLevel host, AABB bounds, Set<Entity> entities) {
            entityHost = host;
            entityQueryBounds = bounds;
            queriedEntities = Set.copyOf(entities);
        }
        private boolean current() {
            for (var level : observations.entrySet()) for (var cell : level.getValue().entrySet()) {
                if (!level.getKey().hasChunkAt(cell.getKey()) || level.getKey().getBlockState(cell.getKey()) != cell.getValue()) return false;
            }
            for (var entity : entityBounds.entrySet()) {
                if (entity.getKey().isRemoved() || !entity.getKey().getBoundingBox().equals(entity.getValue())) return false;
            }
            for (var level : blockEntities.entrySet()) for (var cell : level.getValue().entrySet()) {
                if (!level.getKey().hasChunkAt(cell.getKey()) || level.getKey().getBlockEntity(cell.getKey()) != cell.getValue()) return false;
            }
            if (entityHost != null && !entityCandidates(entityHost, Objects.requireNonNull(entityQueryBounds)).equals(queriedEntities)) return false;
            return true;
        }
    }

    private record LoadedGetter(ServerLevel level, boolean structure, Budget budget,
                                @Nullable BlockPos proposedPosition, @Nullable BlockState proposedState) implements BlockGetter {
        private boolean outside(BlockPos pos) {
            return level.isOutsideBuildHeight(pos) || structure && !NativeStructureDimensions.contains(pos);
        }

        private void check(BlockPos pos) {
            budget.read();
            if (!level.hasChunkAt(pos)) throw new Denied();
        }

        @Override public BlockState getBlockState(BlockPos pos) {
            if (outside(pos)) return Blocks.AIR.defaultBlockState();
            check(pos);
            return pos.equals(proposedPosition) ? Objects.requireNonNull(proposedState) : budget.observeState(level, pos);
        }

        @Override public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
            if (outside(pos)) return null;
            check(pos);
            return pos.equals(proposedPosition) ? null : budget.observeBlockEntity(level, pos);
        }

        @Override public FluidState getFluidState(BlockPos pos) {
            if (outside(pos)) return Fluids.EMPTY.defaultFluidState();
            return getBlockState(pos).getFluidState();
        }

        @Override public int getHeight() { return level.getHeight(); }
        @Override public int getMinY() { return level.getMinY(); }
    }

    private static final class Denied extends RuntimeException {
        private Denied() { super(null, null, false, false); }
    }
}
