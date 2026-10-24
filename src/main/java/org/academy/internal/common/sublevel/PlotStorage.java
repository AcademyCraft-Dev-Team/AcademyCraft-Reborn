package org.academy.internal.common.sublevel;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.academy.AcademyCraft;
import org.academy.internal.common.structure.space.NativeStructureDimensions;
import org.academy.internal.common.structure.space.NativeStructureTickets;
import org.academy.mixin.common.PersistentEntitySectionManagerAccessor;
import org.academy.mixin.common.ServerLevelEntityManagerAccessor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Shared plot storage over the single void plot dimension. Phase 1 prototype: allocation and
 * chunk residency only; block/chunk serialization is added by the plot handle layer.
 */
public final class PlotStorage {
    public static final int LOG_SIZE = 1;
    public static final int ORIGIN_CHUNK = 1_000;
    public static final int MAX_PLOTS = 4_096;
    private static final AtomicLong SERIALS = new AtomicLong();

    private final ServerLevel level;
    private final PlotLayout layout;
    private final PlotAllocator allocator;
    private final PlotAllocator spaciousAllocator;
    private final Map<PlotPos, Lease> leases = new LinkedHashMap<>();
    /** Empty, cleared leases kept resident so a capture does not synchronously generate chunks. */
    private final ArrayDeque<Lease> ready = new ArrayDeque<>();
    /** Entity storage has a separate async lifecycle from block chunks in the paused plot level. */
    private final ArrayDeque<PendingEntityLoad> pendingEntityLoads = new ArrayDeque<>();
    private static final int ENTITY_LOAD_BUDGET = 4;
    private static final boolean TRACE_ENTITY_RESIDENCY = Boolean.getBoolean("academy.sublevel.traceEntityResidency")
            || Boolean.getBoolean("academy.structureRestorationSmoke");
    private boolean reportedEntityWait;
    private boolean reportedEntityReady;
    private PendingPrewarm pendingPrewarm;

    public PlotStorage(ServerLevel level) {
        this(level, new PlotLayout(LOG_SIZE, ORIGIN_CHUNK, ORIGIN_CHUNK, MAX_PLOTS));
    }

    public PlotStorage(ServerLevel level, PlotLayout layout) {
        this.level = Objects.requireNonNull(level, "level");
        this.layout = Objects.requireNonNull(layout, "layout");
        if (!NativeStructureDimensions.isPlotLevel(level.dimension())) {
            throw new IllegalArgumentException("Plot storage requires the shared plot dimension");
        }
        if (!level.getServer().isSameThread()) {
            throw new IllegalStateException("Plot storage is server-thread confined");
        }
        this.allocator = new PlotAllocator(layout);
        this.spaciousAllocator = new PlotAllocator(layout);
    }

    public ServerLevel level() {
        return level;
    }

    public PlotLayout layout() {
        return layout;
    }

    public int allocated() {
        return allocator.allocated() + spaciousAllocator.allocated();
    }

    public int capacity() {
        return allocator.capacity();
    }

    /** Allocates a plot and force-loads every chunk; a partial failure releases everything. */
    public Optional<Lease> allocate() {
        var prewarmed = ready.pollFirst();
        if (prewarmed != null) return Optional.of(prewarmed);
        return allocateFresh();
    }

    /** A legacy owner may temporarily hold one replacement slot even at the normal capacity. */
    Optional<Lease> allocateMigration(PlotPos source) {
        if (PlotLayout.spacious(source) || !leases.containsKey(source)) return Optional.empty();
        if (!ready.isEmpty() || allocated() < capacity()) return allocate();
        if (allocated() != capacity()) return Optional.empty();
        var plot = spaciousAllocator.allocate().map(slot -> new PlotPos(slot.x() + PlotLayout.SPACIOUS_MARKER, slot.z()));
        if (plot.isEmpty()) return Optional.empty();
        var lease = open(plot.get()).orElse(null);
        if (lease == null) return Optional.empty();
        try { clearUnusedContent(lease); return Optional.of(lease); }
        catch (RuntimeException failure) { release(lease); throw failure; }
    }

    private Optional<Lease> allocateFresh() {
        long allocatorStarted = org.academy.internal.common.structure.transaction.CaptureTimings.begin();
        var plot = allocateAnchor();
        org.academy.internal.common.structure.transaction.CaptureTimings.record("plot-storage",
                "plot_allocator", allocatorStarted);
        if (plot.isEmpty()) return Optional.empty();
        long openStarted = org.academy.internal.common.structure.transaction.CaptureTimings.begin();
        var lease = open(plot.get());
        org.academy.internal.common.structure.transaction.CaptureTimings.record("plot-storage",
                "plot_open", openStarted);
        if (lease.isEmpty()) return Optional.empty();
        try {
            long clearStarted = org.academy.internal.common.structure.transaction.CaptureTimings.begin();
            clearUnusedContent(lease.get());
            org.academy.internal.common.structure.transaction.CaptureTimings.record("plot-storage",
                    "plot_clear", clearStarted);
            return lease;
        } catch (RuntimeException | Error failure) {
            release(lease.get());
            throw failure;
        }
    }

    /** Only a fresh allocation is cleared; claiming persisted content must retain its blocks. */
    private void clearUnusedContent(Lease lease) {
        clearChunks(lease.chunks());
    }

    private void clearChunks(java.util.Collection<ChunkPos> chunks) {
        for (var chunkPos : chunks) {
            var chunk = level.getChunk(chunkPos.x(), chunkPos.z());
            var sections = chunk.getSections();
            for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
                var section = sections[sectionIndex];
                if (section.hasOnlyAir()) continue;
                int minY = level.getMinY() + sectionIndex * 16;
                for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
                    if (section.getBlockState(x, y, z).isAir()) continue;
                    var position = new BlockPos(chunkPos.x() * 16 + x, minY + y, chunkPos.z() * 16 + z);
                    if (!level.setBlock(position, Blocks.AIR.defaultBlockState(), SubLevelCaptureService.TRANSACTION_FLAGS)) {
                        throw new IllegalStateException("Could not clear unused plot cell " + position);
                    }
                }
            }
        }
    }

    /** Reclaims a persisted plot identity without moving it; used by restart recovery. */
    public Optional<Lease> claim(PlotPos plot) {
        Objects.requireNonNull(plot, "plot");
        if (leases.containsKey(plot) || !allocatorFor(plot).reserve(slot(plot))) return Optional.empty();
        return open(plot);
    }

    public Optional<Lease> claim(PlotPos plot, net.minecraft.world.phys.AABB bounds) {
        var lease = claim(plot).orElse(null);
        if (lease == null) return Optional.empty();
        try {
            if (!ensureBounds(plot, bounds, false)) { release(lease); return Optional.empty(); }
            return Optional.of(lease);
        } catch (RuntimeException failure) { release(lease); throw failure; }
    }

    private PlotAllocator allocatorFor(PlotPos plot) { return PlotLayout.spacious(plot) ? spaciousAllocator : allocator; }
    private static PlotPos slot(PlotPos plot) {
        return PlotLayout.spacious(plot) ? new PlotPos(plot.x() - PlotLayout.SPACIOUS_MARKER, plot.z()) : plot;
    }
    private Optional<PlotPos> allocateAnchor() {
        if (allocated() >= capacity()) return Optional.empty();
        return spaciousAllocator.allocate().map(plot -> new PlotPos(plot.x() + PlotLayout.SPACIOUS_MARKER, plot.z()));
    }

    private Optional<Lease> open(PlotPos plot) {
        var lease = reserve(plot);
        if (lease == null) return Optional.empty();
        long chunkStarted = org.academy.internal.common.structure.transaction.CaptureTimings.begin();
        try {
            for (var chunk : lease.chunks()) level.getChunk(chunk.x(), chunk.z());
        } catch (RuntimeException failure) {
            release(lease);
            AcademyCraft.LOGGER.error("Could not open plot {}", plot, failure);
            return Optional.empty();
        }
        org.academy.internal.common.structure.transaction.CaptureTimings.record("plot-storage",
                "plot_chunk_load", chunkStarted);
        return Optional.of(lease);
    }

    /** Reserves tickets and the lease without loading chunks; the prewarm path fills them later. */
    private Lease reserve(PlotPos plot) {
        var chunks = new ArrayList<ChunkPos>(layout.sideChunks() * layout.sideChunks());
        var acquired = new ArrayList<ChunkPos>(chunks.size());
        try {
            for (int x = 0; x < layout.sideChunks(); x++) {
                for (int z = 0; z < layout.sideChunks(); z++) {
                    var chunk = new ChunkPos(layout.chunkMinX(plot) + x, layout.chunkMinZ(plot) + z);
                    chunks.add(chunk);
                    if (!NativeStructureTickets.acquire(level, chunk)) {
                        throw new IllegalStateException("Plot ticket was rejected at " + chunk);
                    }
                    acquired.add(chunk);
                }
            }
            var lease = new Lease(SERIALS.incrementAndGet(), plot, List.copyOf(chunks));
            leases.put(plot, lease);
            enqueueEntityLoads(lease);
            return lease;
        } catch (RuntimeException failure) {
            for (var chunk : acquired) {
                try { NativeStructureTickets.release(level, chunk); }
                catch (RuntimeException releaseFailure) { failure.addSuppressed(releaseFailure); }
            }
            allocatorFor(plot).free(slot(plot));
            AcademyCraft.LOGGER.error("Could not reserve plot {}", plot, failure);
            return null;
        }
    }

    /**
     * Loads one plot chunk per call until {@code target} empty leases are ready. Called from the
     * server tick so a capture normally finds a resident, cleared plot instead of synchronously
     * generating four chunks inside the capture call. The same total chunk work is moved into
     * warmup/idle ticks; it is not eliminated.
     */
    public void prewarmTick(int target) {
        maintainEntityLoads();
        if (target <= 0) return;
        if (pendingPrewarm == null) {
            if (ready.size() >= target) return;
            var plot = allocateAnchor().orElse(null);
            if (plot == null) return;
            var lease = reserve(plot);
            if (lease == null) return;
            pendingPrewarm = new PendingPrewarm(lease);
        }
        var pending = pendingPrewarm;
        if (pending.next < pending.lease.chunks().size()) {
            var chunk = pending.lease.chunks().get(pending.next++);
            long started = org.academy.internal.common.structure.transaction.CaptureTimings.begin();
            try {
                level.getChunk(chunk.x(), chunk.z());
            } catch (RuntimeException failure) {
                abortPrewarm();
                AcademyCraft.LOGGER.warn("Could not prewarm plot chunk {}", chunk, failure);
                return;
            }
            org.academy.internal.common.structure.transaction.CaptureTimings.record("plot-storage",
                    "prewarm_chunk", started);
            return;
        }
        try {
            clearUnusedContent(pending.lease);
        } catch (RuntimeException failure) {
            abortPrewarm();
            AcademyCraft.LOGGER.warn("Could not clear prewarmed plot {}", pending.lease.plot(), failure);
            return;
        }
        ready.addLast(pending.lease);
        pendingPrewarm = null;
    }

    /**
     * The plot ServerLevel deliberately does not run ordinary simulation. Queue at most four
     * entity reads per tick and publish only completed reads, without flush/join or chunk loads.
     * EntityStorage deserialization already runs on the server executor. Maintenance must also
     * run when prewarming is disabled so claimed saves and fresh allocations become observable.
     */
    @SuppressWarnings("unchecked")
    private void maintainEntityLoads() {
        var manager = ((ServerLevelEntityManagerAccessor) level).academy$getEntityManager();
        var access = (PersistentEntitySectionManagerAccessor<Entity>) (Object) manager;
        for (int budget = 0; budget < ENTITY_LOAD_BUDGET && !pendingEntityLoads.isEmpty(); budget++) {
            var pending = pendingEntityLoads.peekFirst();
            if (leases.get(pending.lease.plot()) != pending.lease) {
                pendingEntityLoads.removeFirst();
                continue;
            }
            var chunk = pending.lease.chunks().get(pending.next++);
            access.academy$ensureChunkQueuedForLoad(chunk.pack());
            if (pending.next == pending.lease.chunks().size()) pendingEntityLoads.removeFirst();
        }
        // Unlike flush(false), this only consumes already completed async results.
        manager.processPendingLoads();
    }

    private void abortPrewarm() {
        var pending = pendingPrewarm;
        pendingPrewarm = null;
        if (pending != null) release(pending.lease);
    }

    private void enqueueEntityLoads(Lease lease) {
        if (pendingEntityLoads.stream().noneMatch(pending -> pending.lease == lease)) {
            pendingEntityLoads.addLast(new PendingEntityLoad(lease));
        }
    }

    public boolean release(Lease lease) {
        Objects.requireNonNull(lease, "lease");
        if (pendingPrewarm != null && pendingPrewarm.lease == lease) pendingPrewarm = null;
        ready.remove(lease);
        if (!leases.remove(lease.plot(), lease)) return false;
        for (var chunk : lease.chunks()) NativeStructureTickets.release(level, chunk);
        allocatorFor(lease.plot()).free(slot(lease.plot()));
        return true;
    }

    public Optional<Lease> lease(PlotPos plot) {
        return Optional.ofNullable(leases.get(plot));
    }

    /**
     * Snapshots every entity owned by the leased chunks, including hidden sections. A synchronous
     * block chunk load does not make its entities accessible to the ordinary spatial queries.
     * Drain pending entity storage first so staticization cannot release a plot before disk-loaded
     * entities arrive. EntityStorage.flush also runs its main-thread deserialization queue.
     */
    @SuppressWarnings("unchecked")
    public List<Entity> entities(PlotPos plot) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Plot entities require the server thread");
        var lease = lease(plot).orElseThrow(() -> new IllegalStateException("Plot is not leased: " + plot));
        var manager = ((ServerLevelEntityManagerAccessor) level).academy$getEntityManager();
        var access = (PersistentEntitySectionManagerAccessor<Entity>) (Object) manager;
        boolean pending = false;
        for (var chunk : lease.chunks()) {
            if (!manager.areEntitiesLoaded(chunk.pack())) {
                access.academy$ensureChunkQueuedForLoad(chunk.pack());
                pending = true;
            }
        }
        if (pending) {
            access.academy$getPermanentStorage().flush(false);
            manager.processPendingLoads();
        }
        var result = new ArrayList<Entity>();
        for (var chunk : lease.chunks()) {
            if (!manager.areEntitiesLoaded(chunk.pack())) {
                throw new IllegalStateException("Plot entities are not loaded at " + chunk);
            }
            access.academy$getSectionStorage().getExistingSectionsInChunk(chunk.pack())
                    .flatMap(section -> section.getEntities())
                    .filter(entity -> !entity.isRemoved()).forEach(result::add);
        }
        return List.copyOf(result);
    }

    /** Non-loading entity observation for maintenance previews; unknown storage defers the transfer. */
    @SuppressWarnings("unchecked")
    public Optional<List<Entity>> loadedEntities(PlotPos plot) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Plot entities require the server thread");
        var lease = lease(plot).orElse(null);
        if (lease == null) return Optional.empty();
        var manager = ((ServerLevelEntityManagerAccessor) level).academy$getEntityManager();
        var access = (PersistentEntitySectionManagerAccessor<Entity>) (Object) manager;
        var result = new ArrayList<Entity>();
        for (var chunk : lease.chunks()) {
            if (!manager.areEntitiesLoaded(chunk.pack()) || level.getChunkSource().getChunkNow(chunk.x(), chunk.z()) == null) {
                if (TRACE_ENTITY_RESIDENCY && !reportedEntityWait) {
                    reportedEntityWait = true;
                    AcademyCraft.LOGGER.info("SubLevel entity observation deferred: {}", entityLoadDiagnostics(plot));
                }
                return Optional.empty();
            }
            access.academy$getSectionStorage().getExistingSectionsInChunk(chunk.pack())
                    .flatMap(section -> section.getEntities()).filter(entity -> !entity.isRemoved()).forEach(result::add);
        }
        if (TRACE_ENTITY_RESIDENCY && !reportedEntityReady) {
            reportedEntityReady = true;
            AcademyCraft.LOGGER.info("SubLevel entity observation ready: {} entities={}", entityLoadDiagnostics(plot), result.size());
        }
        return Optional.of(List.copyOf(result));
    }

    /** Pure residency evidence for diagnostics; never queues reads or loads chunks. */
    public String entityLoadDiagnostics(PlotPos plot) {
        var lease = leases.get(plot);
        if (lease == null) return "plot=" + plot + ",lease=absent";
        var manager = ((ServerLevelEntityManagerAccessor) level).academy$getEntityManager();
        return "plot=" + plot + ",queued_leases=" + pendingEntityLoads.size() + ",chunks="
                + lease.chunks().stream().map(chunk -> chunk + "[block="
                + (level.getChunkSource().getChunkNow(chunk.x(), chunk.z()) != null)
                + ",entities=" + manager.areEntitiesLoaded(chunk.pack()) + "]").toList();
    }

    /** Releases every outstanding plot; called only when the owning runtime stops. */
    public void close() {
        pendingPrewarm = null;
        ready.clear();
        pendingEntityLoads.clear();
        for (var lease : List.copyOf(leases.values())) release(lease);
    }

    private static final class PendingPrewarm {
        private final Lease lease;
        private int next;

        private PendingPrewarm(Lease lease) {
            this.lease = lease;
        }
    }

    private static final class PendingEntityLoad {
        private final Lease lease;
        private int next;

        private PendingEntityLoad(Lease lease) {
            this.lease = lease;
        }
    }

    public BlockState blockState(PlotPos plot, BlockPos local) {
        return resident(plot, local) ? level.getBlockState(worldPosition(plot, local)) : Blocks.AIR.defaultBlockState();
    }

    public boolean setBlock(PlotPos plot, BlockPos local, BlockState state, int flags) {
        Objects.requireNonNull(state, "state");
        if (!resident(plot, local)) throw new IllegalArgumentException("Plot cell is not leased: " + local);
        return level.setBlock(worldPosition(plot, local), state, flags);
    }

    public BlockEntity blockEntity(PlotPos plot, BlockPos local) {
        return resident(plot, local) ? level.getBlockEntity(worldPosition(plot, local)) : null;
    }

    public BlockPos worldPosition(PlotPos plot, BlockPos local) {
        Objects.requireNonNull(local, "local");
        if (!layout.containsLocal(plot, local.getX(), local.getZ())) {
            throw new IllegalArgumentException("Local plot position is outside the plot footprint");
        }
        return new BlockPos(layout.chunkMinX(plot) * 16 + local.getX(), local.getY(),
                layout.chunkMinZ(plot) * 16 + local.getZ());
    }

    public BlockPos localPosition(PlotPos plot, BlockPos world) {
        Objects.requireNonNull(world, "world");
        int x = world.getX() - layout.chunkMinX(plot) * 16;
        int z = world.getZ() - layout.chunkMinZ(plot) * 16;
        if (x < -PlotLayout.SPACIOUS_RADIUS || z < -PlotLayout.SPACIOUS_RADIUS
                || x >= PlotLayout.SPACIOUS_RADIUS || z >= PlotLayout.SPACIOUS_RADIUS) {
            throw new IllegalArgumentException("World position is outside the plot footprint");
        }
        return new BlockPos(x, world.getY(), z);
    }

    public boolean resident(PlotPos plot, BlockPos local) {
        if (!layout.containsLocal(plot, local.getX(), local.getZ()) || local.getY() < level.getMinY() || local.getY() >= level.getMaxY()) return false;
        var lease = leases.get(plot);
        var world = worldPosition(plot, local);
        return lease != null && lease.chunks.contains(new ChunkPos(world.getX() >> 4, world.getZ() >> 4));
    }

    /** Loads only the chunks containing the edited image, never the whole reserved virtual slot. */
    public boolean ensureBounds(PlotPos plot, net.minecraft.world.phys.AABB bounds) {
        return ensureBounds(plot, bounds, true);
    }

    private boolean ensureBounds(PlotPos plot, net.minecraft.world.phys.AABB bounds, boolean clearNewChunks) {
        var lease = leases.get(plot);
        if (lease == null || !SubLevelGrowth.valid(bounds, level.getMinY(), level.getMaxY())
                || !layout.containsLocal(plot, (int) Math.floor(bounds.minX), (int) Math.floor(bounds.minZ))
                || !layout.containsLocal(plot, (int) Math.ceil(bounds.maxX) - 1, (int) Math.ceil(bounds.maxZ) - 1)) return false;
        var first = worldPosition(plot, BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ));
        var last = worldPosition(plot, new BlockPos((int) Math.ceil(bounds.maxX) - 1, (int) Math.ceil(bounds.maxY) - 1, (int) Math.ceil(bounds.maxZ) - 1));
        var added = new ArrayList<ChunkPos>();
        try {
            for (int x = first.getX() >> 4; x <= (last.getX() >> 4); x++) for (int z = first.getZ() >> 4; z <= (last.getZ() >> 4); z++) {
                var chunk = new ChunkPos(x, z);
                if (lease.chunks.contains(chunk)) continue;
                if (!NativeStructureTickets.acquire(level, chunk)) throw new IllegalStateException("Cannot expand plot residency");
                added.add(chunk);
                level.getChunk(x, z);
            }
            if (clearNewChunks) clearChunks(added);
            lease.chunks.addAll(added);
            if (!added.isEmpty()) enqueueEntityLoads(lease);
            return true;
        } catch (RuntimeException failure) {
            for (var chunk : added) NativeStructureTickets.release(level, chunk);
            AcademyCraft.LOGGER.error("Cannot expand plot {}", plot, failure);
            return false;
        }
    }

    public static final class Lease {
        private final long serial;
        private final PlotPos plot;
        private final java.util.LinkedHashSet<ChunkPos> chunks;
        public Lease(long serial, PlotPos plot, List<ChunkPos> chunks) {
            this.serial = serial;
            this.plot = Objects.requireNonNull(plot, "plot");
            this.chunks = new java.util.LinkedHashSet<>(chunks);
            if (chunks.isEmpty()) throw new IllegalArgumentException("A plot lease requires chunks");
        }
        public long serial() { return serial; }
        public PlotPos plot() { return plot; }
        public List<ChunkPos> chunks() { return List.copyOf(chunks); }
    }
}
