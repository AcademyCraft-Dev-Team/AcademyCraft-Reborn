package org.academy.internal.common.structure.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.academy.internal.common.structure.space.NativeStructureChanges;
import org.academy.internal.common.structure.space.NativeStructureDimensions;
import org.academy.internal.common.structure.space.NativeStructureView;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntConsumer;

/** Server-thread, bounded collision observations; never drains the presentation dirty-cell queue. */
public final class NativeStructurePhysicsGeometry {
    public static final int MAX_STRUCTURE_CELLS = 128;
    public static final int MAX_TERRAIN_CELLS = 65_536;
    static final int MAX_READS = MAX_TERRAIN_CELLS * 2;
    private static final double WORLD_LIMIT = 30_000_000;
    private static final AABB EMPTY_BOUNDS = new AABB(0, 0, 0, 0, 0, 0);
    // These exact vanilla classes obtain collision geometry from state, not actor/BE/tick data.
    // In particular, no class-hierarchy test admits a mod subclass with different callbacks.
    private static final Set<Class<?>> STATE_SHAPES = Set.of(Block.class, RotatedPillarBlock.class,
            SlabBlock.class, StairBlock.class, FenceBlock.class, WallBlock.class, GrassBlock.class,
            SnowyBlock.class, MyceliumBlock.class, SandBlock.class, ColoredFallingBlock.class,
            FarmlandBlock.class, DirtPathBlock.class, MudBlock.class, DropExperienceBlock.class, RedStoneOreBlock.class,
            RedstoneLampBlock.class, FurnaceBlock.class, BlastFurnaceBlock.class, SmokerBlock.class,
            DispenserBlock.class, DropperBlock.class, HopperBlock.class, LeverBlock.class,
            LeavesBlock.class, TintedParticleLeavesBlock.class, UntintedParticleLeavesBlock.class,
            MangroveLeavesBlock.class);
    // These exact vanilla plants inherit the ordinary noCollision shape. Terrain may ignore
    // them, but captured structure contents still go through the stricter supported() gate.
    // Do not generalize this to every empty CollisionContext.empty() result: powder snow and
    // other actor-conditioned geometry can be solid for the interacting entity.
    private static final Set<Class<?>> EMPTY_TERRAIN_PLANTS = Set.of(TallGrassBlock.class,
            DoublePlantBlock.class, FlowerBlock.class, TallFlowerBlock.class, SaplingBlock.class, MushroomBlock.class,
            DryVegetationBlock.class, ShortDryGrassBlock.class, TallDryGrassBlock.class,
            BushBlock.class, FlowerBedBlock.class, LeafLitterBlock.class);

    private NativeStructurePhysicsGeometry() {}

    public enum Failure {
        INVALID_BOUNDS, UNLOADED_CHUNK, LIMIT_EXCEEDED, UNSUPPORTED_CONTENT, UNSTABLE_SOURCE, INVALID_SHAPE
    }

    public static final class GeometryException extends IllegalStateException {
        private final Failure failure;

        private GeometryException(Failure failure, String message) {
            super(message);
            this.failure = failure;
        }

        public Failure failure() { return failure; }
    }

    /** Boxes are in storage-local coordinates; a zero-box structure has zero bounds. */
    public static Snapshot captureStructure(ServerLevel storage) {
        requireThread(storage);
        if (!NativeStructureDimensions.isStorageLevel(storage.dimension())
                || storage.getHeight() > NativeStructureView.HEIGHT) {
            throw denied(Failure.INVALID_BOUNDS, "Expected a bounded native structure storage level");
        }
        long revision = NativeStructureChanges.revision(storage);
        var getter = new LoadedGetter(storage, true, null);
        var result = new ArrayList<PhysicsBox>();
        int nonAir = 0;
        for (int chunkX = -2; chunkX < 2; chunkX++) for (int chunkZ = -2; chunkZ < 2; chunkZ++) {
            var chunk = getter.chunk(chunkX, chunkZ);
            var sections = chunk.getSections();
            for (int index = 0; index < sections.length; index++) {
                var section = sections[index];
                if (section.hasOnlyAir()) continue;
                int baseY = storage.getMinY() + index * 16;
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                    if (section.getBlockState(x, y, z).isAir()) continue;
                    if (++nonAir > MAX_STRUCTURE_CELLS) {
                        throw denied(Failure.LIMIT_EXCEEDED, "Structure exceeds the physical non-air cell budget");
                    }
                    var position = new BlockPos(chunkX * 16 + x, baseY + y, chunkZ * 16 + z);
                    captureCell(getter, position, PhysicsVector.ZERO, NativePhysicsWorld.MAX_BOXES, result);
                }
            }
        }
        // Complex shapes can split one cell into many cuboids. Merge adjacent full cubes first;
        // a plain cuboid structure then fits a single dynamic body regardless of cell count.
        var merged = mergeFullBlocks(result);
        if (merged.size() > NativePhysicsWorld.MAX_BOXES) {
            throw denied(Failure.LIMIT_EXCEEDED, "Structure requires more collision boxes than the world budget");
        }
        return finish(getter, merged, revision, nonAir);
    }

    /**
     * Deterministically replaces adjacent full-block cuboids with larger cuboids. Only exact
     * 1x1x1 axis-aligned cells are merged, so partial shapes, slabs, stairs and fences retain
     * their audited geometry. The result is sorted by center to keep snapshots and digests stable.
     */
    static List<PhysicsBox> mergeFullBlocks(List<PhysicsBox> boxes) {
        var remaining = new java.util.TreeSet<BlockPos>(java.util.Comparator
                .comparingInt((BlockPos pos) -> pos.getY())
                .thenComparingInt(pos -> pos.getX()).thenComparingInt(pos -> pos.getZ()));
        var others = new ArrayList<PhysicsBox>();
        for (var box : boxes) {
            if (isFullBlock(box)) {
                remaining.add(new BlockPos((int) Math.floor(box.center().x()),
                        (int) Math.floor(box.center().y()), (int) Math.floor(box.center().z())));
            } else others.add(box);
        }
        while (!remaining.isEmpty()) {
            var start = remaining.first();
            int maxX = start.getX();
            while (remaining.contains(new BlockPos(maxX + 1, start.getY(), start.getZ()))) maxX++;
            int maxZ = start.getZ();
            while (columnOfFullBlocks(remaining, start.getX(), maxX, start.getY(), maxZ + 1)) maxZ++;
            int maxY = start.getY();
            while (slabOfFullBlocks(remaining, start.getX(), maxX, start.getZ(), maxZ, maxY + 1)) maxY++;
            for (int y = start.getY(); y <= maxY; y++) for (int x = start.getX(); x <= maxX; x++) {
                for (int z = start.getZ(); z <= maxZ; z++) {
                    remaining.remove(new BlockPos(x, y, z));
                }
            }
            others.add(new PhysicsBox(
                    new PhysicsVector((start.getX() + maxX + 1) / 2.0, (start.getY() + maxY + 1) / 2.0,
                            (start.getZ() + maxZ + 1) / 2.0), PhysicsQuaternion.IDENTITY,
                    new PhysicsVector((maxX - start.getX() + 1) / 2.0, (maxY - start.getY() + 1) / 2.0,
                            (maxZ - start.getZ() + 1) / 2.0)));
        }
        others.sort(java.util.Comparator.comparingDouble((PhysicsBox box) -> box.center().x())
                .thenComparingDouble(box -> box.center().y()).thenComparingDouble(box -> box.center().z()));
        return List.copyOf(others);
    }

    private static boolean columnOfFullBlocks(java.util.Set<BlockPos> remaining, int minX, int maxX, int y, int z) {
        for (int x = minX; x <= maxX; x++) if (!remaining.contains(new BlockPos(x, y, z))) return false;
        return true;
    }

    private static boolean slabOfFullBlocks(java.util.Set<BlockPos> remaining, int minX, int maxX,
                                            int minZ, int maxZ, int y) {
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            if (!remaining.contains(new BlockPos(x, y, z))) return false;
        }
        return true;
    }

    private static boolean isFullBlock(PhysicsBox box) {
        var half = box.halfExtents();
        var center = box.center();
        if (!box.rotation().equals(PhysicsQuaternion.IDENTITY)
                || !near(half.x(), 0.5) || !near(half.y(), 0.5) || !near(half.z(), 0.5)) return false;
        return near(center.x() - Math.floor(center.x()), 0.5)
                && near(center.y() - Math.floor(center.y()), 0.5)
                && near(center.z() - Math.floor(center.z()), 0.5);
    }

    private static boolean near(double value, double expected) {
        return Math.abs(value - expected) <= 1.0E-9;
    }

    /**
     * Includes a one-cell halo for shapes such as fences extending outside their own cell.
     * Boxes and bounds are relative to origin; each batch can become one fixed native body.
     * maxBoxes is the caller's remaining world budget, after reserving all dynamic bodies.
     * Host water/lava contribute no solid geometry when their collision shape is empty; this
     * does not simulate buoyancy. Audited waterlogged blocks retain their actual solid shape.
     */
    public static Snapshot captureTerrain(ServerLevel host, AABB worldBounds, PhysicsVector origin, int maxBoxes) {
        var scan = terrainScan(host, worldBounds, origin, maxBoxes);
        return captureTerrainPartition(host, scan, scan.inflate(1), origin, maxBoxes, MAX_READS, ignored -> {});
    }

    /** Planning performs the same gates for a warm cache and a complete direct extraction. */
    static AABB terrainScan(ServerLevel host, AABB worldBounds, PhysicsVector origin, int maxBoxes) {
        requireThread(host);
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(worldBounds, "worldBounds");
        if (host.getServer().getLevel(host.dimension()) != host
                || NativeStructureDimensions.isStorageLevel(host.dimension()) || maxBoxes < 0
                || maxBoxes > NativePhysicsWorld.MAX_BOXES || !validBounds(worldBounds)
                || !host.getWorldBorder().isWithinBounds(worldBounds)) {
            throw denied(Failure.INVALID_BOUNDS, "Invalid terrain region or collision budget");
        }
        int minX = floor(worldBounds.minX) - 1;
        int minY = Math.max(host.getMinY(), floor(worldBounds.minY) - 1);
        int minZ = floor(worldBounds.minZ) - 1;
        int maxX = floor(worldBounds.maxX) + 1;
        int maxY = Math.min(host.getMaxY(), floor(worldBounds.maxY) + 1);
        int maxZ = floor(worldBounds.maxZ) + 1;
        requireCellBudget(minX, minY, minZ, maxX, maxY, maxZ);
        return new AABB(minX, minY, minZ, maxX + 1.0, maxY + 1.0, maxZ + 1.0);
    }

    /** A partition owns cells, not clipped shapes; neighbours may use the original full read domain. */
    static Snapshot captureTerrainPartition(ServerLevel host, AABB cells, AABB readBounds,
                                             PhysicsVector origin, int maxBoxes, int maxReads,
                                             IntConsumer scannedCells) {
        requireThread(host);
        if (maxReads < 0 || maxReads > MAX_READS) throw denied(Failure.LIMIT_EXCEEDED, "Invalid remaining terrain read budget");
        int minX = (int) cells.minX, minY = (int) cells.minY, minZ = (int) cells.minZ;
        int maxX = (int) cells.maxX - 1, maxY = (int) cells.maxY - 1, maxZ = (int) cells.maxZ - 1;
        var getter = new LoadedGetter(host, false, readBounds, maxReads);
        for (int x = minX >> 4; x <= maxX >> 4; x++) {
            for (int z = minZ >> 4; z <= maxZ >> 4; z++) getter.chunk(x, z);
        }
        var result = new ArrayList<PhysicsBox>();
        int nonAir = 0;
        int scanned = 0;
        try {
            for (var mutable : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
                var position = mutable.immutable();
                scanned++;
                if (captureCell(getter, position, origin, maxBoxes, result)) nonAir++;
            }
            return finish(getter, result, -1, nonAir);
        } finally {
            scannedCells.accept(scanned);
        }
    }

    /** Combines observations without losing air cells, neighbour reads or chunk identities. */
    static Snapshot combineTerrain(ServerLevel host, List<Snapshot> partitions, int maxBoxes) {
        requireThread(host);
        var observations = new ArrayList<LoadedGetter>();
        var boxes = new ArrayList<PhysicsBox>();
        int reads = 0, nonAir = 0;
        for (var partition : partitions) {
            reads = Math.addExact(reads, partition.readCount());
            requireTerrainBudget(Math.addExact(boxes.size(), partition.boxes.size()), reads, maxBoxes);
            for (var getter : partition.observations) {
                if (getter.level != host || getter.structure) throw denied(Failure.INVALID_BOUNDS, "Cannot combine unrelated terrain observations");
                observations.add(getter);
            }
            boxes.addAll(partition.boxes);
            nonAir = Math.addExact(nonAir, partition.nonAirCells);
        }
        var combined = new Snapshot(observations, boxes, -1, nonAir);
        if (!combined.stillValid()) throw denied(Failure.UNSTABLE_SOURCE, "Terrain changed while combining partitions");
        return combined;
    }

    static void requireTerrainBudget(int boxes, int reads, int maxBoxes) {
        if (maxBoxes < 0 || maxBoxes > NativePhysicsWorld.MAX_BOXES || boxes < 0 || boxes > maxBoxes
                || reads < 0 || reads > MAX_READS) {
            throw denied(Failure.LIMIT_EXCEEDED, "Combined terrain exceeds the collision or read budget");
        }
    }

    private static Snapshot finish(LoadedGetter getter, List<PhysicsBox> boxes, long revision, int nonAir) {
        var snapshot = new Snapshot(getter, boxes, revision, nonAir);
        if (!snapshot.stillValid()) throw denied(Failure.UNSTABLE_SOURCE, "Geometry changed during extraction");
        return snapshot;
    }

    private static boolean captureCell(LoadedGetter getter, BlockPos position, PhysicsVector origin,
                                       int maxBoxes, List<PhysicsBox> result) {
        var state = getter.getBlockState(position);
        if (state.isAir()) return false;
        if (!supported(state) && (getter.structure || !supportedWaterloggedTerrain(state))) {
            if (!getter.structure && (emptyTerrainPlant(state) || state.is(Blocks.WATER) || state.is(Blocks.LAVA))
                    && state.getCollisionShape(getter, position, CollisionContext.empty()).isEmpty()) {
                // getBlockState above remains in the receipt: replacing a plant/fluid with a solid
                // block must invalidate the cached terrain even though it contributed no boxes.
                return true;
            }
            throw denied(Failure.UNSUPPORTED_CONTENT, "Unsupported collision state at " + position + ": " + state);
        }
        var shape = state.getCollisionShape(getter, position, CollisionContext.empty());
        var offset = new PhysicsVector(position.getX() - origin.x(), position.getY() - origin.y(), position.getZ() - origin.z());
        result.addAll(decompose(shape, offset, maxBoxes - result.size()));
        return true;
    }

    /** Entity-conditioned, dynamic, fluid and unknown BE shapes require a future explicit adapter. */
    static boolean supported(BlockState state) {
        var block = state.getBlock();
        if (!state.getFluidState().isEmpty() || block.hasDynamicShape()
                || !BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals("minecraft")) return false;
        if (block == Blocks.CHEST) return NativeStructureView.isSingleChestState(state);
        return STATE_SHAPES.contains(block.getClass());
    }

    private static boolean emptyTerrainPlant(BlockState state) {
        var block = state.getBlock();
        return state.getFluidState().isEmpty() && !state.hasBlockEntity() && !block.hasDynamicShape()
                && BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals("minecraft")
                && EMPTY_TERRAIN_PLANTS.contains(block.getClass());
    }

    private static boolean supportedWaterloggedTerrain(BlockState state) {
        // Audit the same block's dry state, but sample the original state's actual collision.
        // This admits waterlogged slabs/stairs/fences without weakening the captured-content gate
        // or treating arbitrary fluid-containing/actor-conditioned blocks as safe terrain.
        return state.hasProperty(BlockStateProperties.WATERLOGGED)
                && state.getValue(BlockStateProperties.WATERLOGGED) && state.getFluidState().is(Fluids.WATER)
                && supported(state.setValue(BlockStateProperties.WATERLOGGED, false));
    }

    /** Exact VoxelShape box decomposition: never substitutes the shape's bounding cuboid. */
    static List<PhysicsBox> decompose(VoxelShape shape, PhysicsVector offset, int remaining) {
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(offset, "offset");
        if (remaining < 0 || remaining > NativePhysicsWorld.MAX_BOXES) {
            throw denied(Failure.LIMIT_EXCEEDED, "Invalid remaining collision box budget");
        }
        // Count before allocating toAabbs(), so a complex shape cannot allocate beyond this budget.
        int[] count = {0};
        shape.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
            if (++count[0] > Math.min(remaining, NativePhysicsWorld.MAX_BOXES_PER_BODY)) {
                throw denied(Failure.LIMIT_EXCEEDED, "Collision shape exceeds the remaining box budget");
            }
        });
        var result = new ArrayList<PhysicsBox>(count[0]);
        for (var box : shape.toAabbs()) result.add(convert(box, offset));
        return List.copyOf(result);
    }

    private static PhysicsBox convert(AABB box, PhysicsVector offset) {
        if (!finite(box) || box.minX < -1 || box.minY < -1 || box.minZ < -1
                || box.maxX > 2 || box.maxY > 2 || box.maxZ > 2
                || box.maxX - box.minX < 2.0E-6 || box.maxY - box.minY < 2.0E-6 || box.maxZ - box.minZ < 2.0E-6) {
            throw denied(Failure.INVALID_SHAPE, "Collision box is outside the audited cell/extent bounds");
        }
        try {
            return new PhysicsBox(new PhysicsVector(offset.x() + (box.minX + box.maxX) * 0.5,
                    offset.y() + (box.minY + box.maxY) * 0.5, offset.z() + (box.minZ + box.maxZ) * 0.5),
                    PhysicsQuaternion.IDENTITY, new PhysicsVector((box.maxX - box.minX) * 0.5,
                    (box.maxY - box.minY) * 0.5, (box.maxZ - box.minZ) * 0.5));
        } catch (IllegalArgumentException invalid) {
            throw denied(Failure.INVALID_SHAPE, "Collision box exceeds the native coordinate bounds");
        }
    }

    static List<List<PhysicsBox>> batches(List<PhysicsBox> boxes) {
        if (boxes.size() > NativePhysicsWorld.MAX_BOXES) throw denied(Failure.LIMIT_EXCEEDED, "World box budget exceeded");
        var result = new ArrayList<List<PhysicsBox>>();
        for (int offset = 0; offset < boxes.size(); offset += NativePhysicsWorld.MAX_BOXES_PER_BODY) {
            result.add(List.copyOf(boxes.subList(offset, Math.min(boxes.size(), offset + NativePhysicsWorld.MAX_BOXES_PER_BODY))));
        }
        return List.copyOf(result);
    }

    static void requireCellBudget(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        long x = (long) maxX - minX + 1;
        long y = (long) maxY - minY + 1;
        long z = (long) maxZ - minZ + 1;
        if (x <= 0 || y <= 0 || z <= 0) throw denied(Failure.INVALID_BOUNDS, "Terrain region has no cells");
        if (x > MAX_TERRAIN_CELLS || y > MAX_TERRAIN_CELLS || z > MAX_TERRAIN_CELLS
                || x * y > MAX_TERRAIN_CELLS || x * y * z > MAX_TERRAIN_CELLS) {
            throw denied(Failure.LIMIT_EXCEEDED, "Terrain cell budget exceeded before reading chunks");
        }
    }

    /** Immutable output plus a receipt retaining every observed state and loaded chunk identity. */
    public static final class Snapshot {
        private final List<LoadedGetter> observations;
        private final List<PhysicsBox> boxes;
        private final AABB bounds;
        private final long sourceRevision;
        private final int nonAirCells;

        private Snapshot(LoadedGetter observations, List<PhysicsBox> boxes, long sourceRevision, int nonAirCells) {
            this(List.of(observations), boxes, sourceRevision, nonAirCells);
        }

        private Snapshot(List<LoadedGetter> observations, List<PhysicsBox> boxes, long sourceRevision, int nonAirCells) {
            this.observations = List.copyOf(observations);
            this.boxes = List.copyOf(boxes);
            this.sourceRevision = sourceRevision;
            this.nonAirCells = nonAirCells;
            AABB bounds = null;
            for (var box : boxes) {
                var center = box.center();
                var half = box.halfExtents();
                var next = new AABB(center.x() - half.x(), center.y() - half.y(), center.z() - half.z(),
                        center.x() + half.x(), center.y() + half.y(), center.z() + half.z());
                bounds = bounds == null ? next : bounds.minmax(next);
            }
            this.bounds = bounds == null ? EMPTY_BOUNDS : bounds;
        }

        public List<PhysicsBox> boxes() { return boxes; }
        public List<List<PhysicsBox>> batches() { return NativeStructurePhysicsGeometry.batches(boxes); }
        public AABB bounds() { return bounds; }
        public long sourceRevision() { return sourceRevision; }
        public int nonAirCells() { return nonAirCells; }

        int readCount() {
            int total = 0;
            for (var getter : observations) total = Math.addExact(total, getter.reads);
            return total;
        }

        /** No shape/BE callbacks and no loads. Call after extraction callbacks and immediately before use. */
        public boolean stillValid() {
            for (var observation : observations) {
                var level = observation.level;
                requireThread(level);
                if (level.getServer().getLevel(level.dimension()) != level
                        || observation.structure && NativeStructureChanges.revision(level) != sourceRevision) return false;
                for (var entry : observation.chunks.entrySet()) {
                    var pos = entry.getKey();
                    if (level.getChunkSource().getChunkNow(pos.x(), pos.z()) != entry.getValue()) return false;
                }
                for (var entry : observation.states.entrySet()) {
                    var pos = entry.getKey();
                    var chunk = observation.chunks.get(new ChunkKey(pos.getX() >> 4, pos.getZ() >> 4));
                    if (chunk == null || chunk.getBlockState(pos) != entry.getValue()) return false;
                }
            }
            return true;
        }
    }

    private record ChunkKey(int x, int z) {}

    private static final class LoadedGetter implements BlockGetter {
        private final ServerLevel level;
        private final boolean structure;
        private final @Nullable AABB readBounds;
        private final Map<ChunkKey, LevelChunk> chunks = new HashMap<>();
        private final Map<BlockPos, BlockState> states = new HashMap<>();
        private final int maxReads;
        private int reads;

        private LoadedGetter(ServerLevel level, boolean structure, @Nullable AABB readBounds) {
            this(level, structure, readBounds, MAX_READS);
        }

        private LoadedGetter(ServerLevel level, boolean structure, @Nullable AABB readBounds, int maxReads) {
            this.level = level;
            this.structure = structure;
            this.readBounds = readBounds;
            this.maxReads = maxReads;
        }

        private LevelChunk chunk(int x, int z) {
            var current = level.getChunkSource().getChunkNow(x, z);
            if (current == null) throw denied(Failure.UNLOADED_CHUNK, "Physics refuses to load chunk " + x + ", " + z);
            var previous = chunks.putIfAbsent(new ChunkKey(x, z), current);
            if (previous != null && previous != current) throw denied(Failure.UNSTABLE_SOURCE, "Chunk identity changed during geometry read");
            return current;
        }

        @Override public BlockState getBlockState(BlockPos position) {
            if (++reads > maxReads) throw denied(Failure.LIMIT_EXCEEDED, "Collision shape state-read budget exceeded");
            if (level.isOutsideBuildHeight(position)) return Blocks.AIR.defaultBlockState();
            if (structure && !NativeStructureDimensions.contains(position)) {
                throw denied(Failure.UNSUPPORTED_CONTENT, "Collision shape depends on cells outside its storage container");
            }
            if (readBounds != null && (position.getX() < readBounds.minX || position.getX() >= readBounds.maxX
                    || position.getY() < readBounds.minY || position.getY() >= readBounds.maxY
                    || position.getZ() < readBounds.minZ || position.getZ() >= readBounds.maxZ)) {
                throw denied(Failure.LIMIT_EXCEEDED, "Collision shape reads outside the bounded terrain neighbourhood");
            }
            var state = chunk(position.getX() >> 4, position.getZ() >> 4).getBlockState(position);
            var previous = states.putIfAbsent(position.immutable(), state);
            if (previous != null && previous != state) throw denied(Failure.UNSTABLE_SOURCE, "Block state changed during geometry read");
            return state;
        }

        @Override public @Nullable BlockEntity getBlockEntity(BlockPos position) {
            throw denied(Failure.UNSUPPORTED_CONTENT, "Block entity dependent collision shape requires an adapter");
        }

        @Override public FluidState getFluidState(BlockPos position) { return getBlockState(position).getFluidState(); }
        @Override public int getHeight() { return level.getHeight(); }
        @Override public int getMinY() { return level.getMinY(); }
    }

    private static boolean validBounds(AABB bounds) {
        return finite(bounds) && bounds.minX <= bounds.maxX && bounds.minY <= bounds.maxY && bounds.minZ <= bounds.maxZ
                && bounds.minX >= -WORLD_LIMIT && bounds.maxX <= WORLD_LIMIT
                && bounds.minY >= -WORLD_LIMIT && bounds.maxY <= WORLD_LIMIT
                && bounds.minZ >= -WORLD_LIMIT && bounds.maxZ <= WORLD_LIMIT;
    }

    private static boolean finite(AABB bounds) {
        return Double.isFinite(bounds.minX) && Double.isFinite(bounds.minY) && Double.isFinite(bounds.minZ)
                && Double.isFinite(bounds.maxX) && Double.isFinite(bounds.maxY) && Double.isFinite(bounds.maxZ);
    }

    private static int floor(double value) { return (int) Math.floor(value); }
    static GeometryException denied(Failure failure, String message) { return new GeometryException(failure, message); }

    private static void requireThread(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Physics geometry requires the server thread");
    }
}
