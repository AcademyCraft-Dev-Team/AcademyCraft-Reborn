package org.academy.internal.common.ability.teleport;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Client-side cache for the 区块跃迁 map.
 *
 * <p>The server streams per-chunk tiles of {@code detail×detail} texels; the client stores them in a
 * sparse per-dimension map and hands received patches to listeners. Tile application is incremental —
 * a full resync happens only when the texture re-anchors, never per packet, which is what keeps the
 * screen responsive while tiles stream in.
 *
 * <p>A rolling epoch guard drops patches from a view the user has already navigated away from.
 */
public final class ChunkMapClientState {
    /** Written by the screen when a new view request is sent, so stale patches can be ignored. */
    private static volatile int activeEpoch;

    private static final Map<String, DimensionMap> MAPS = new HashMap<>();
    private static final List<Listener> LISTENERS = new ArrayList<>();
    private static final List<SwapResult> PENDING_SWAPS = new ArrayList<>();
    private static final List<TeleportResult> PENDING_TELEPORTS = new ArrayList<>();
    private static final Map<UUID, PreloadProgress> PRELOAD_PROGRESS = new HashMap<>();
    private static final List<ChunkLeapPackets.TilesPacket> TILE_PATCHES = new ArrayList<>();

    private ChunkMapClientState() {
    }

    /** Per-dimension tile store plus load counters for the progress bar. */
    public static final class DimensionMap {
        private final Map<Long, int[]> tiles = new HashMap<>();
        private final Map<Integer, List<ChunkLeapPackets.Marker>> markersByEpoch = new HashMap<>();
        private int loaded;
        private int total;

        /** Tile of {@code texelCount} ARGB values for a chunk, or null when it has not arrived. */
        public synchronized int[] tile(int chunkX, int chunkZ) {
            return tiles.get(ChunkPos.pack(chunkX, chunkZ));
        }

        public synchronized boolean hasTile(int chunkX, int chunkZ) {
            return tiles.containsKey(ChunkPos.pack(chunkX, chunkZ));
        }

        public synchronized int loaded() {
            return loaded;
        }

        public synchronized int total() {
            return total;
        }

        public synchronized List<ChunkLeapPackets.Marker> markers() {
            var result = new ArrayList<ChunkLeapPackets.Marker>();
            for (var markers : markersByEpoch.values()) {
                result.addAll(markers);
            }
            return result;
        }

        public synchronized void clear() {
            tiles.clear();
            markersByEpoch.clear();
            loaded = 0;
            total = 0;
        }
    }

    /** Observers (the screen) get woken when data arrives. */
    public interface Listener {
        void onMapDataChanged();
    }

    public record SwapResult(UUID opId, boolean success, String reasonKey,
                             List<ChunkLeapRegion> affected) {
    }

    public record TeleportResult(boolean success, String reasonKey) {
    }

    public static void addListener(Listener listener) {
        synchronized (LISTENERS) {
            LISTENERS.add(listener);
        }
    }

    public static void removeListener(Listener listener) {
        synchronized (LISTENERS) {
            LISTENERS.remove(listener);
        }
    }

    public static void beginView(ResourceKey<Level> dimension, int epoch) {
        activeEpoch = epoch;
    }

    public static DimensionMap map(String dimensionId) {
        synchronized (MAPS) {
            return MAPS.computeIfAbsent(dimensionId, ignored -> new DimensionMap());
        }
    }

    public static void acceptTiles(ChunkLeapPackets.TilesPacket packet) {
        var map = map(packet.dimensionId());
        synchronized (map) {
            for (var tile : packet.tiles()) {
                map.tiles.put(ChunkPos.pack(tile.chunkX(), tile.chunkZ()), tile.texels());
            }
        }
        // Patches are queued, not applied: the renderer drains them into its texture incrementally.
        synchronized (TILE_PATCHES) {
            if (TILE_PATCHES.size() < 64) {
                TILE_PATCHES.add(packet);
            }
        }
        notifyListeners();
    }

    /** Drains queued tile patches for incremental application, oldest first. */
    public static List<ChunkLeapPackets.TilesPacket> drainTilePatches() {
        synchronized (TILE_PATCHES) {
            if (TILE_PATCHES.isEmpty()) return List.of();
            var copy = List.copyOf(TILE_PATCHES);
            TILE_PATCHES.clear();
            return copy;
        }
    }

    public static void acceptEntities(ChunkLeapPackets.EntitiesPacket packet) {
        var map = map(packet.dimensionId());
        synchronized (map) {
            map.markersByEpoch.put(activeEpoch, List.copyOf(packet.markers()));
        }
        notifyListeners();
    }

    public static void acceptViewStatus(ChunkLeapPackets.ViewStatusPacket packet) {
        var map = map(packet.dimensionId());
        synchronized (map) {
            map.loaded = packet.loaded();
            map.total = packet.total();
        }
        notifyListeners();
    }

    /** Records destination preload progress for an in-flight operation, keyed by operation id. */
    public static void acceptPreloadStatus(ChunkLeapPackets.PreloadStatusPacket packet) {
        synchronized (PRELOAD_PROGRESS) {
            PRELOAD_PROGRESS.put(packet.opId(), new PreloadProgress(
                    packet.opId(), packet.dimensionId(), packet.ready(), packet.loaded(), packet.total()));
        }
        notifyListeners();
    }

    /** Latest preload progress for {@code opId}, or null when none has arrived yet. */
    public static PreloadProgress preloadProgress(UUID opId) {
        if (opId == null) return null;
        synchronized (PRELOAD_PROGRESS) {
            return PRELOAD_PROGRESS.get(opId);
        }
    }

    /** Progress of loading a swap's destination regions. */
    public record PreloadProgress(UUID opId, String dimensionId, boolean ready,
                                  int loaded, int total) {
        public float fraction() {
            return total <= 0 ? (ready ? 1f : 0f) : Math.min(1f, loaded / (float) total);
        }
    }

    /** Stores the most recent inspection result, keyed by "dimension:chunkX:chunkZ". */
    public static void acceptInspectResult(ChunkLeapPackets.InspectResultPacket packet) {
        synchronized (INSPECTIONS) {
            INSPECTIONS.put(inspectKey(packet.dimensionId(), packet.chunkX(), packet.chunkZ()), packet);
        }
        notifyListeners();
    }

    /** Last inspection for a chunk, or null when it has not been requested yet. */
    public static ChunkLeapPackets.InspectResultPacket inspection(String dimensionId, int chunkX, int chunkZ) {
        synchronized (INSPECTIONS) {
            return INSPECTIONS.get(inspectKey(dimensionId, chunkX, chunkZ));
        }
    }

    private static String inspectKey(String dimensionId, int chunkX, int chunkZ) {
        return dimensionId + ":" + chunkX + ":" + chunkZ;
    }

    private static final Map<String, ChunkLeapPackets.InspectResultPacket> INSPECTIONS = new HashMap<>();
    public static void handleSwapResult(ChunkLeapPackets.SwapResultPacket packet) {
        if (packet.success()) {
            // Drop stale tiles so the next view request repaints the swapped areas.
            for (var region : packet.affected()) {
                var affectedMap = map(region.dimension().identifier().toString());
                synchronized (affectedMap) {
                    for (var x = region.minChunkX(); x <= region.maxChunkX(); x++) {
                        for (var z = region.minChunkZ(); z <= region.maxChunkZ(); z++) {
                            affectedMap.tiles.remove(ChunkPos.pack(x, z));
                        }
                    }
                }
            }
        }
        synchronized (PENDING_SWAPS) {
            PENDING_SWAPS.add(new SwapResult(packet.opId(), packet.success(), packet.reasonKey(),
                    packet.affected()));
        }
        notifyListeners();
    }

    public static void handleTeleportResult(ChunkLeapPackets.TeleportResultPacket packet) {
        synchronized (PENDING_TELEPORTS) {
            PENDING_TELEPORTS.add(new TeleportResult(packet.success(), packet.reasonKey()));
        }
        notifyListeners();
    }

    /** Drains queued swap results (the screen shows the failure reason and refreshes). */
    public static List<SwapResult> drainSwapResults() {
        synchronized (PENDING_SWAPS) {
            var copy = List.copyOf(PENDING_SWAPS);
            PENDING_SWAPS.clear();
            return copy;
        }
    }

    public static List<TeleportResult> drainTeleportResults() {
        synchronized (PENDING_TELEPORTS) {
            var copy = List.copyOf(PENDING_TELEPORTS);
            PENDING_TELEPORTS.clear();
            return copy;
        }
    }

    /**
     * Drops cached terrain for one dimension, so the next view request refetches it.
     *
     * <p>Used by the manual refresh: the cache is keyed by dimension and chunk, and a chunk that changed
     * while it was not being watched would otherwise keep its stale tile indefinitely.
     */
    public static void invalidateDimension(String dimensionId) {
        var map = map(dimensionId);
        synchronized (map) {
            map.clear();
        }
        synchronized (TILE_PATCHES) {
            TILE_PATCHES.clear();
        }
        notifyListeners();
    }
    public static void clear() {
        synchronized (MAPS) {
            MAPS.clear();
        }
        synchronized (PENDING_SWAPS) {
            PENDING_SWAPS.clear();
        }
        synchronized (PENDING_TELEPORTS) {
            PENDING_TELEPORTS.clear();
        }
        synchronized (TILE_PATCHES) {
            TILE_PATCHES.clear();
        }
        synchronized (INSPECTIONS) {
            INSPECTIONS.clear();
        }
        activeEpoch = 0;
    }

    /** Chunks in {@code region} whose tiles have not arrived; drives the loading gate. */
    public static int missingTileCount(ResourceKey<Level> dimension, ChunkLeapRegion region) {
        var map = map(dimension.identifier().toString());
        var missing = 0;
        synchronized (map) {
            for (var x = region.minChunkX(); x <= region.maxChunkX(); x++) {
                for (var z = region.minChunkZ(); z <= region.maxChunkZ(); z++) {
                    if (!map.hasTile(x, z)) missing++;
                }
            }
        }
        return missing;
    }

    private static void notifyListeners() {
        List<Listener> snapshot;
        synchronized (LISTENERS) {
            snapshot = List.copyOf(LISTENERS);
        }
        for (var listener : snapshot) {
            listener.onMapDataChanged();
        }
    }
}
