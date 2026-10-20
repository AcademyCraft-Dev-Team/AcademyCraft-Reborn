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
    private final Map<PlotPos, Lease> leases = new LinkedHashMap<>();

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
    }

    public ServerLevel level() {
        return level;
    }

    public PlotLayout layout() {
        return layout;
    }

    public int allocated() {
        return allocator.allocated();
    }

    public int capacity() {
        return allocator.capacity();
    }

    /** Allocates a plot and force-loads every chunk; a partial failure releases everything. */
    public Optional<Lease> allocate() {
        var plot = allocator.allocate();
        if (plot.isEmpty()) return Optional.empty();
        var lease = open(plot.get());
        if (lease.isEmpty()) return Optional.empty();
        try {
            clearUnusedContent(lease.get());
            return lease;
        } catch (RuntimeException | Error failure) {
            release(lease.get());
            throw failure;
        }
    }

    /** Only a fresh allocation is cleared; claiming persisted content must retain its blocks. */
    private void clearUnusedContent(Lease lease) {
        for (var chunkPos : lease.chunks()) {
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
        if (leases.containsKey(plot) || !allocator.reserve(plot)) return Optional.empty();
        return open(plot);
    }

    private Optional<Lease> open(PlotPos plot) {
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
                    level.getChunk(chunk.x(), chunk.z());
                }
            }
            var lease = new Lease(SERIALS.incrementAndGet(), plot, List.copyOf(chunks));
            leases.put(plot, lease);
            return Optional.of(lease);
        } catch (RuntimeException failure) {
            for (var chunk : acquired) {
                try { NativeStructureTickets.release(level, chunk); }
                catch (RuntimeException releaseFailure) { failure.addSuppressed(releaseFailure); }
            }
            allocator.free(plot);
            AcademyCraft.LOGGER.error("Could not open plot {}", plot, failure);
            return Optional.empty();
        }
    }

    public boolean release(Lease lease) {
        Objects.requireNonNull(lease, "lease");
        if (!leases.remove(lease.plot(), lease)) return false;
        for (var chunk : lease.chunks()) NativeStructureTickets.release(level, chunk);
        allocator.free(lease.plot());
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

    /** Releases every outstanding plot; called only when the owning runtime stops. */
    public void close() {
        for (var lease : List.copyOf(leases.values())) release(lease);
    }

    public BlockState blockState(PlotPos plot, BlockPos local) {
        return level.getBlockState(worldPosition(plot, local));
    }

    public boolean setBlock(PlotPos plot, BlockPos local, BlockState state, int flags) {
        Objects.requireNonNull(state, "state");
        return level.setBlock(worldPosition(plot, local), state, flags);
    }

    public BlockEntity blockEntity(PlotPos plot, BlockPos local) {
        return level.getBlockEntity(worldPosition(plot, local));
    }

    public BlockPos worldPosition(PlotPos plot, BlockPos local) {
        Objects.requireNonNull(local, "local");
        int sideBlocks = layout.sideChunks() * 16;
        if (local.getX() < 0 || local.getZ() < 0 || local.getX() >= sideBlocks || local.getZ() >= sideBlocks) {
            throw new IllegalArgumentException("Local plot position is outside the plot footprint");
        }
        return new BlockPos(layout.chunkMinX(plot) * 16 + local.getX(), local.getY(),
                layout.chunkMinZ(plot) * 16 + local.getZ());
    }

    public BlockPos localPosition(PlotPos plot, BlockPos world) {
        Objects.requireNonNull(world, "world");
        int sideBlocks = layout.sideChunks() * 16;
        int x = world.getX() - layout.chunkMinX(plot) * 16;
        int z = world.getZ() - layout.chunkMinZ(plot) * 16;
        if (x < 0 || z < 0 || x >= sideBlocks || z >= sideBlocks) {
            throw new IllegalArgumentException("World position is outside the plot footprint");
        }
        return new BlockPos(x, world.getY(), z);
    }

    public record Lease(long serial, PlotPos plot, List<ChunkPos> chunks) {
        public Lease {
            Objects.requireNonNull(plot, "plot");
            chunks = List.copyOf(chunks);
            if (chunks.isEmpty()) throw new IllegalArgumentException("A plot lease requires chunks");
        }
    }
}
