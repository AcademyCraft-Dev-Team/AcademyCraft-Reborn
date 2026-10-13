package org.academy.internal.common.structure.physics;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One region's bounded terrain shape cache. Cache hits still inspect every recorded state,
 * including air, and every loaded chunk identity; only shape extraction is reused.
 * No tickets, global change hooks, structure geometry or persistent state are owned here.
 */
public final class NativeStructureTerrainCache {
    private final ServerLevel host;
    private final AABB bounds;
    private final PhysicsVector origin;
    private Map<AABB, NativeStructurePhysicsGeometry.Snapshot> partitions = Map.of();
    private @Nullable AABB cachedScan;
    private NativeStructurePhysicsGeometry.@Nullable Snapshot aggregate;
    private long captures;
    private long hits;
    private long scannedCells;

    public NativeStructureTerrainCache(ServerLevel host, AABB bounds, PhysicsVector origin) {
        this.host = Objects.requireNonNull(host, "host");
        this.bounds = Objects.requireNonNull(bounds, "bounds");
        this.origin = Objects.requireNonNull(origin, "origin");
    }

    /**
     * Stages all dirty partitions and revalidates the entire result before installing it.
     * A failed capture never returns the previous aggregate. The current dynamic-body box
     * allowance and the original whole-query state-read limit also apply to warm partitions.
     */
    public NativeStructurePhysicsGeometry.Snapshot capture(int maxBoxes) {
        requireThread();
        try {
            var scan = NativeStructurePhysicsGeometry.terrainScan(host, bounds, origin, maxBoxes);
            if (scan.equals(cachedScan) && aggregate != null && aggregate.stillValid()) {
                NativeStructurePhysicsGeometry.requireTerrainBudget(aggregate.boxes().size(), aggregate.readCount(), maxBoxes);
                NativeStructurePhysicsGeometry.terrainScan(host, bounds, origin, maxBoxes);
                hits = Math.addExact(hits, partitions.size());
                return aggregate;
            }
            var readBounds = scan.inflate(1);
            var candidates = new LinkedHashMap<AABB, NativeStructurePhysicsGeometry.Snapshot>();
            boolean unchanged = scan.equals(cachedScan) && aggregate != null;
            int boxes = 0, reads = 0;
            for (var cells : partitionBounds(scan)) {
                var previous = scan.equals(cachedScan) ? partitions.get(cells) : null;
                NativeStructurePhysicsGeometry.Snapshot next;
                if (previous != null && previous.stillValid()) {
                    next = previous;
                    hits = Math.incrementExact(hits);
                } else {
                    next = NativeStructurePhysicsGeometry.captureTerrainPartition(host, cells, readBounds, origin,
                            maxBoxes - boxes, NativeStructurePhysicsGeometry.MAX_READS - reads,
                            count -> scannedCells = Math.addExact(scannedCells, count));
                    captures = Math.incrementExact(captures);
                    unchanged = false;
                }
                boxes = Math.addExact(boxes, next.boxes().size());
                reads = Math.addExact(reads, next.readCount());
                NativeStructurePhysicsGeometry.requireTerrainBudget(boxes, reads, maxBoxes);
                candidates.put(cells, next);
            }
            var result = unchanged ? Objects.requireNonNull(aggregate)
                    : NativeStructurePhysicsGeometry.combineTerrain(host, List.copyOf(candidates.values()), maxBoxes);
            if (!result.stillValid()) {
                throw NativeStructurePhysicsGeometry.denied(NativeStructurePhysicsGeometry.Failure.UNSTABLE_SOURCE,
                        "Terrain changed before committing the cached observation");
            }
            // Shape callbacks may change world-wide gates without changing any observed block.
            if (!scan.equals(NativeStructurePhysicsGeometry.terrainScan(host, bounds, origin, maxBoxes))) {
                throw NativeStructurePhysicsGeometry.denied(NativeStructurePhysicsGeometry.Failure.UNSTABLE_SOURCE,
                        "Terrain scan bounds changed during extraction");
            }
            // No callbacks occur between the last observation and this cache-only publication.
            if (!unchanged) {
                partitions = candidates;
                cachedScan = scan;
                aggregate = result;
            }
            return result;
        } catch (NativeStructurePhysicsGeometry.GeometryException unavailable) {
            if (unavailable.failure() == NativeStructurePhysicsGeometry.Failure.UNLOADED_CHUNK) clear();
            throw unavailable;
        }
    }

    /** Drops every retained chunk/state/shape reference, while retaining cumulative work counters. */
    public void clear() {
        requireThread();
        partitions = Map.of();
        cachedScan = null;
        aggregate = null;
    }

    /**
     * Captures count successfully extracted partitions and hits count validated reused partitions.
     * Scanned cells count attempted owned cells, including partial failed extractions. Those work
     * counters are cumulative even if a later partition or aggregate check fails. Partitions is
     * the size of the last committed cache, and becomes zero after clear.
     */
    public record Diagnostics(long captures, long hits, long scannedCells, int partitions) {}

    public Diagnostics diagnostics() {
        requireThread();
        return new Diagnostics(captures, hits, scannedCells, partitions.size());
    }

    /** Integral half-open owned-cell ranges, in stable order independent of cache dirtiness. */
    static List<AABB> partitionBounds(AABB scan) {
        Objects.requireNonNull(scan, "scan");
        for (double coordinate : new double[]{scan.minX, scan.minY, scan.minZ, scan.maxX, scan.maxY, scan.maxZ}) {
            if (!Double.isFinite(coordinate) || coordinate != Math.rint(coordinate)
                    || coordinate < Integer.MIN_VALUE + 16.0 || coordinate > Integer.MAX_VALUE - 16.0) {
                throw new IllegalArgumentException("Terrain partitions require bounded integral cell ranges");
            }
        }
        int minX = (int) scan.minX, minY = (int) scan.minY, minZ = (int) scan.minZ;
        int maxX = (int) scan.maxX - 1, maxY = (int) scan.maxY - 1, maxZ = (int) scan.maxZ - 1;
        NativeStructurePhysicsGeometry.requireCellBudget(minX, minY, minZ, maxX, maxY, maxZ);
        var result = new ArrayList<AABB>();
        for (int z = minZ >> 4; z <= maxZ >> 4; z++) {
            for (int y = minY >> 4; y <= maxY >> 4; y++) {
                for (int x = minX >> 4; x <= maxX >> 4; x++) {
                    result.add(new AABB(Math.max(minX, x * 16), Math.max(minY, y * 16), Math.max(minZ, z * 16),
                            Math.min(maxX + 1, x * 16 + 16), Math.min(maxY + 1, y * 16 + 16), Math.min(maxZ + 1, z * 16 + 16)));
                }
            }
        }
        return List.copyOf(result);
    }

    private void requireThread() {
        if (!host.getServer().isSameThread()) throw new IllegalStateException("Terrain caching requires the server thread");
    }
}
