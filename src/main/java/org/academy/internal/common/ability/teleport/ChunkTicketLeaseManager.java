package org.academy.internal.common.ability.teleport;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Reference-counted chunk loading leases backed by {@link ChunkLeapTickets#MAP_VIEW}.
 *
 * <p>Distinct from {@link TeleportChunkForceManager}: that helper uses {@code setChunkForced}, which
 * loads synchronously and is capped at 64 chunks per lease, and {@code TicketType.FORCED} persists
 * visited chunks to disk. Map exploration needs the opposite — many chunks, asynchronous loading,
 * no disk residue — so this manager uses a non-persistent, non-simulating ticket and reference counts
 * each chunk so overlapping views (two players, or a view plus a preload) do not fight.
 *
 * <p>Two acquisition paths. Swap preloads use {@link #acquireAndLoad}: immediate and future-bearing,
 * bounded by the swap cap, paid for by an explicit player action. Map views use
 * {@link #requestViewLease}: the desired rectangle is recorded and the tickets are added by a per-tick
 * budget, because a view can cover hundreds of chunks and adding that many distance-manager entries
 * in one server tick is exactly what stalls the server when the map opens.
 */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class ChunkTicketLeaseManager {
    /** Ticket additions per server tick across all view leases. */
    private static final int VIEW_TICKET_BUDGET = 48;
    /** Ticket removals per server tick across all view leases. */
    private static final int VIEW_REMOVE_BUDGET = 96;
    /** Hard ceiling on chunks held by a single view lease; must match the client view cap. */
    public static final int VIEW_LEASE_CAP = 1600;
    /** Hard ceiling on chunks held by a single immediate (swap preload) lease. */
    public static final int IMMEDIATE_LEASE_CAP = 1024;

    /** Immediate leases: whole set acquired up front, released as a unit. */
    private static final Map<String, Lease> LEASES = new HashMap<>();
    /** Desired-but-maybe-not-yet-materialised view leases, drained on tick. */
    private static final Map<String, ViewLease> VIEW_LEASES = new HashMap<>();
    private static final Map<ChunkKey, Integer> REFS = new HashMap<>();

    /** A set of chunks kept loaded until the owning id is released or {@link #releaseAll()} runs. */
    public record Lease(ServerLevel level, List<ChunkPos> chunks, int radius) {
    }

    /** A view lease: the desired rectangle and the chunks this owner actually holds tickets for. */
    private static final class ViewLease {
        private final ServerLevel level;
        private ChunkLeapRegion region;
        private final Set<Long> held = new HashSet<>();

        private ViewLease(ServerLevel level, ChunkLeapRegion region) {
            this.level = level;
            this.region = region;
        }
    }

    private record ChunkKey(Level level, long chunk, int radius) {
    }

    private ChunkTicketLeaseManager() {
    }

    // ---------------------------------------------------------------- immediate (swap preload)

    public static synchronized Lease acquire(ServerLevel level, String owner, ChunkLeapRegion region) {
        return acquire(level, owner, region, 0);
    }

    /**
     * Keeps every chunk of {@code region} loaded until {@code owner} is released.
     *
     * @param radius extra ticket radius, so neighbours provide light and collision context
     */
    public static synchronized Lease acquire(ServerLevel level, String owner, ChunkLeapRegion region, int radius) {
        if (level == null || owner == null || owner.isBlank() || region == null) return null;
        release(owner);
        var chunks = new ArrayList<ChunkPos>();
        for (var x = region.minChunkX(); x <= region.maxChunkX(); x++) {
            for (var z = region.minChunkZ(); z <= region.maxChunkZ(); z++) {
                chunks.add(new ChunkPos(x, z));
                if (chunks.size() >= IMMEDIATE_LEASE_CAP) break;
            }
        }
        var safeRadius = Math.max(0, Math.min(2, radius));
        for (var pos : chunks) {
            acquireTicket(level, pos, safeRadius);
        }
        var lease = new Lease(level, List.copyOf(chunks), safeRadius);
        LEASES.put(owner, lease);
        return lease;
    }

    /**
     * Acquires {@code owner} and returns a future that completes once every chunk of {@code region} is
     * at {@code ChunkStatus.FULL}, including the neighbour radius held for lighting and collision context.
     * Never blocks the server thread.
     */
    public static CompletableFuture<Void> acquireAndLoad(ServerLevel level, String owner, ChunkLeapRegion region) {
        var lease = acquire(level, owner, region, 1);
        if (lease == null) return CompletableFuture.failedFuture(
                new IllegalArgumentException("Invalid chunk preload lease"));
        var futures = new CompletableFuture<?>[lease.chunks.size()];
        CompletableFuture<Void> loading;
        try {
            for (var i = 0; i < lease.chunks.size(); i++) {
                // Await the same radius ticket acquired above. Using radius zero here creates a second,
                // untracked NO_TIMEOUT ticket which release(owner) can never remove.
                futures[i] = level.getChunkSource()
                        .addTicketAndLoadWithRadius(ChunkLeapTickets.MAP_VIEW.get(), lease.chunks.get(i), lease.radius);
            }
            loading = awaitLoads(futures);
        } catch (RuntimeException exception) {
            loading = CompletableFuture.failedFuture(exception);
        }
        return loading.thenRunAsync(() -> {
            for (var pos : lease.chunks) {
                for (var dx = -lease.radius; dx <= lease.radius; dx++) {
                    for (var dz = -lease.radius; dz <= lease.radius; dz++) {
                        if (level.getChunkSource().getChunkNow(pos.x() + dx, pos.z() + dz) == null) {
                            throw new IllegalStateException("Chunk preload is not resident in "
                                    + level.dimension().identifier() + " at " + (pos.x() + dx) + "," + (pos.z() + dz));
                        }
                    }
                }
            }
        }, level.getServer()).whenCompleteAsync((ignored, failure) -> {
            if (failure != null) {
                synchronized (ChunkTicketLeaseManager.class) {
                    // An older load must not release a replacement request's tickets.
                    if (LEASES.get(owner) == lease) release(owner);
                }
            }
        }, level.getServer());
    }

    /** A completed future can still contain an unloaded ChunkResult rather than loaded chunks. */
    static CompletableFuture<Void> awaitLoads(CompletableFuture<?>... loads) {
        var checked = new CompletableFuture<?>[loads.length];
        for (var i = 0; i < loads.length; i++) {
            checked[i] = loads[i].thenAccept(result -> {
                if (result instanceof ChunkResult<?> chunkResult && !chunkResult.isSuccess()) {
                    throw new IllegalStateException("Chunk preload failed: " + chunkResult.getError());
                }
            });
        }
        return CompletableFuture.allOf(checked);
    }

    public static synchronized void release(String owner) {
        var lease = LEASES.remove(owner);
        if (lease == null) return;
        for (var pos : lease.chunks) {
            releaseTicket(lease.level, pos, lease.radius);
        }
    }

    // ---------------------------------------------------------------- budgeted (map view)

    /**
     * Records the desired view rectangle for {@code owner}. Tickets are materialised by
     * {@link #drainViewLeases()} over subsequent ticks; replacing the region (pan/zoom) or calling
     * {@link #cancelViewLease} converges without a spike.
     */
    public static synchronized void requestViewLease(ServerLevel level, String owner, ChunkLeapRegion region) {
        if (level == null || owner == null || owner.isBlank() || region == null) return;
        var existing = VIEW_LEASES.get(owner);
        if (existing != null && existing.level == level) {
            existing.region = clampViewRegion(region);
            return;
        }
        // Switching level: the old tickets are removed by the next drain pass.
        VIEW_LEASES.put(owner, new ViewLease(level, clampViewRegion(region)));
    }

    /** Drops the view lease for {@code owner}; removal itself is also budgeted. */
    public static synchronized void cancelViewLease(String owner) {
        var lease = VIEW_LEASES.get(owner);
        if (lease != null) lease.region = null;
    }

    private static ChunkLeapRegion clampViewRegion(ChunkLeapRegion region) {
        if (region.chunkCount() <= VIEW_LEASE_CAP) return region;
        // Shrink around the centre until the area fits.
        var width = region.width();
        var height = region.height();
        while ((long) width * height > VIEW_LEASE_CAP) {
            if (width >= height && width > 1) width--;
            else if (height > 1) height--;
            else break;
        }
        return ChunkLeapRegion.ofChunks(region.dimension(),
                region.minChunkX() + (region.width() - width) / 2,
                region.minChunkZ() + (region.height() - height) / 2,
                width, height);
    }

    @SubscribeEvent
    public static synchronized void onServerTick(ServerTickEvent.Post event) {
        drainViewLeases();
    }

    private static void drainViewLeases() {
        var additions = VIEW_TICKET_BUDGET;
        var removals = VIEW_REMOVE_BUDGET;
        var iterator = VIEW_LEASES.entrySet().iterator();
        while (iterator.hasNext() && (additions > 0 || removals > 0)) {
            var entry = iterator.next();
            var lease = entry.getValue();
            if (lease.level.getServer().isStopped()) {
                iterator.remove();
                continue;
            }
            var desired = new HashSet<Long>();
            if (lease.region != null) {
                for (var x = lease.region.minChunkX(); x <= lease.region.maxChunkX(); x++) {
                    for (var z = lease.region.minChunkZ(); z <= lease.region.maxChunkZ(); z++) {
                        desired.add(ChunkPos.pack(x, z));
                    }
                }
            }
            // Remove first so a panned-away view frees chunks for the new one in the same budget.
            for (var posLong : List.copyOf(lease.held)) {
                if (removals <= 0) break;
                if (desired.contains(posLong)) continue;
                releaseTicket(lease.level, ChunkPos.unpack(posLong), 0);
                lease.held.remove(posLong);
                removals--;
            }
            for (var posLong : desired) {
                if (additions <= 0) break;
                if (!lease.held.add(posLong)) continue;
                acquireTicket(lease.level, ChunkPos.unpack(posLong), 0);
                additions--;
            }
            // Cancelled and fully drained: forget the entry.
            if (lease.region == null && lease.held.isEmpty()) {
                iterator.remove();
            }
        }
    }

    // ---------------------------------------------------------------- shared bookkeeping

    private static void acquireTicket(ServerLevel level, ChunkPos pos, int radius) {
        var key = new ChunkKey(level, pos.pack(), radius);
        var count = REFS.get(key);
        if (count == null) {
            level.getChunkSource().addTicketWithRadius(ChunkLeapTickets.MAP_VIEW.get(), pos, radius);
            REFS.put(key, 1);
        } else {
            REFS.put(key, count + 1);
        }
    }

    private static void releaseTicket(ServerLevel level, ChunkPos pos, int radius) {
        var key = new ChunkKey(level, pos.pack(), radius);
        var count = REFS.get(key);
        if (count == null) return;
        if (count > 1) {
            REFS.put(key, count - 1);
            return;
        }
        REFS.remove(key);
        level.getChunkSource().removeTicketWithRadius(ChunkLeapTickets.MAP_VIEW.get(), pos, radius);
    }

    public static synchronized void releaseAll() {
        for (var owner : Set.copyOf(LEASES.keySet())) {
            release(owner);
        }
        LEASES.clear();
        // Shutdown path: drop every view ticket immediately, budget or not.
        for (var lease : VIEW_LEASES.values()) {
            for (var posLong : List.copyOf(lease.held)) {
                releaseTicket(lease.level, ChunkPos.unpack(posLong), 0);
                lease.held.remove(posLong);
            }
        }
        VIEW_LEASES.clear();
        REFS.clear();
    }

    public static String viewOwner(UUID player, ResourceKey<Level> dimension) {
        return "chunk_leap_view:" + player + ":" + dimension.identifier();
    }

    public static String preloadOwner(UUID player) {
        return "chunk_leap_preload:" + player;
    }
}
