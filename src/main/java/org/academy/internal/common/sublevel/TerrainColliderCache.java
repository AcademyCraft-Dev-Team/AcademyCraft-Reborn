package org.academy.internal.common.sublevel;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import org.academy.AcademyCraft;
import org.academy.internal.common.structure.physics.BodyType;
import org.academy.internal.common.structure.physics.NativePhysicsBody;
import org.academy.internal.common.structure.physics.NativePhysicsWorld;
import org.academy.internal.common.structure.physics.PhysicsPose;
import org.academy.internal.common.structure.physics.PhysicsQuaternion;
import org.academy.internal.common.structure.physics.PhysicsVector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Bounded ChunkSection terrain cache for one shared physics universe. Only sections around
 * active SubLevels are materialized; far sections are closed on a short grace period.
 */
public final class TerrainColliderCache implements AutoCloseable {
    public static final int DEFAULT_MAX_SECTIONS = 16;
    public static final int DEFAULT_MAX_BOXES = 28_000;
    private static final int BOXES_PER_SECTION = 256;

    private final ServerLevel host;
    private final NativePhysicsWorld world;
    private final int maxSections;
    private final int maxBoxes;
    private final Map<SectionKey, Entry> sections = new LinkedHashMap<>();
    private final Map<SectionKey, Integer> retryAt = new LinkedHashMap<>();
    private int updates;
    private int skipped;

    public TerrainColliderCache(ServerLevel host, NativePhysicsWorld world) {
        this(host, world, DEFAULT_MAX_SECTIONS, DEFAULT_MAX_BOXES);
    }

    public TerrainColliderCache(ServerLevel host, NativePhysicsWorld world, int maxSections) {
        this(host, world, maxSections, DEFAULT_MAX_BOXES);
    }

    public TerrainColliderCache(ServerLevel host, NativePhysicsWorld world, int maxSections, int maxBoxes) {
        this.host = Objects.requireNonNull(host, "host");
        this.world = Objects.requireNonNull(world, "world");
        if (maxSections < 1 || maxSections > 64) throw new IllegalArgumentException("Terrain section budget must be in [1, 64]");
        if (maxBoxes < 1) throw new IllegalArgumentException("Terrain box budget must be positive");
        if (host.getServer() == null) throw new IllegalArgumentException("Terrain host is not attached to a server");
        this.maxSections = maxSections;
        this.maxBoxes = maxBoxes;
    }

    /** Materializes/removes sections around active world bounds; safe to call once per server tick. */
    public Diagnostics update(Collection<AABB> activeBounds) {
        Objects.requireNonNull(activeBounds, "activeBounds");
        updates = Math.incrementExact(updates);
        var candidates = new java.util.LinkedHashSet<SectionKey>();
        for (var bounds : activeBounds) {
            if (bounds == null || !finite(bounds)) continue;
            int minX = floorSection(bounds.minX) - 1;
            int minY = Math.max(host.getMinY() >> 4, floorSection(bounds.minY) - 1);
            int minZ = floorSection(bounds.minZ) - 1;
            int maxX = floorSection(bounds.maxX) + 1;
            int maxY = floorSection(bounds.maxY) + 1;
            int maxZ = floorSection(bounds.maxZ) + 1;
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= Math.min(host.getMaxY() >> 4, maxY); y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        candidates.add(new SectionKey(x, y, z));
                    }
                }
            }
        }
        var ordered = new ArrayList<>(candidates);
        ordered.sort(java.util.Comparator.comparingDouble(key -> distanceToBounds(key, activeBounds)));
        candidates.clear();
        candidates.addAll(ordered);
        var needed = new LinkedHashMap<SectionKey, AABB>();
        for (var key : candidates) {
            if (needed.size() >= maxSections) break;
            needed.putIfAbsent(key, sectionBounds(key));
        }
        for (var key : List.copyOf(sections.keySet())) {
            if (!needed.containsKey(key)) {
                var entry = sections.get(key);
                if (++entry.stale > 1) remove(key);
            }
        }
        int created = 0;
        for (var entry : needed.entrySet()) {
            var existing = sections.get(entry.getKey());
            if (existing != null) {
                existing.stale = 0;
                continue;
            }
            if (sections.size() >= maxSections) { skipped++; continue; }
            var key = entry.getKey();
            if (retryAt.getOrDefault(key, 0) > updates) continue;
            if (add(key)) {
                retryAt.remove(key);
                created++;
            } else {
                retryAt.put(key, updates + 200);
            }
        }
        int boxes = sections.values().stream().mapToInt(entry -> entry.boxes).sum();
        return new Diagnostics(sections.size(), boxes, created, skipped);
    }

    public Diagnostics diagnostics() {
        int boxes = sections.values().stream().mapToInt(entry -> entry.boxes).sum();
        return new Diagnostics(sections.size(), boxes, 0, skipped);
    }

    public void clear() {
        for (var key : List.copyOf(sections.keySet())) remove(key);
        retryAt.clear();
    }

    @Override
    public void close() {
        clear();
    }

    private boolean add(SectionKey key) {
        var bodies = new ArrayList<NativePhysicsBody>();
        try {
            // Keep synchronous chunk generation out of the normal path: only already-loaded
            // chunks are eligible for the first terrain pass.
            if (host.getChunkSource().getChunkNow(key.x(), key.z()) == null) { skipped++; return false; }
            var geometry = TerrainSectionGeometry.capture(host, key.x(), key.y(), key.z());
            if (geometry.boxes().isEmpty()) return false;
            int currentBoxes = sections.values().stream().mapToInt(entry -> entry.boxes).sum();
            if (geometry.boxes().size() > maxBoxes - currentBoxes) {
                skipped++;
                AcademyCraft.LOGGER.debug("Skipping terrain section {}: terrain box budget {}/{}",
                        key, currentBoxes, maxBoxes);
                return false;
            }
            var origin = world.origin();
            var pose = new PhysicsPose(new PhysicsVector(key.x() * 16 - origin.x(),
                    key.y() * 16 - origin.y(), key.z() * 16 - origin.z()), PhysicsQuaternion.IDENTITY);
            for (int start = 0; start < geometry.boxes().size(); start += BOXES_PER_SECTION) {
                var batch = List.copyOf(geometry.boxes().subList(start,
                        Math.min(geometry.boxes().size(), start + BOXES_PER_SECTION)));
                bodies.add(world.createBody(BodyType.FIXED, pose, batch, 0.0, 0.6, 0.0, false));
            }
            sections.put(key, new Entry(List.copyOf(bodies), geometry.boxes().size()));
            AcademyCraft.LOGGER.debug("Terrain section added {} boxes={} bodies={}", key,
                    geometry.boxes().size(), bodies.size());
            return true;
        } catch (RuntimeException | AssertionError failure) {
            for (var body : bodies) {
                try { body.close(); }
                catch (RuntimeException closeFailure) { failure.addSuppressed(closeFailure); }
            }
            skipped++;
            AcademyCraft.LOGGER.debug("Skipping terrain section {}: {}", key, failure.toString());
            return false;
        }
    }

    private void remove(SectionKey key) {
        var entry = sections.remove(key);
        if (entry == null) return;
        for (var body : entry.bodies) {
            try { body.close(); }
            catch (RuntimeException failure) { AcademyCraft.LOGGER.warn("Terrain body close failed", failure); }
        }
    }

    private double distanceToBounds(SectionKey key, Collection<AABB> activeBounds) {
        var center = sectionBounds(key).getCenter();
        double best = Double.POSITIVE_INFINITY;
        for (var bounds : activeBounds) {
            if (bounds == null || !finite(bounds)) continue;
            double dx = Math.max(Math.max(bounds.minX - center.x, center.x - bounds.maxX), 0.0);
            double dy = Math.max(Math.max(bounds.minY - center.y, center.y - bounds.maxY), 0.0);
            double dz = Math.max(Math.max(bounds.minZ - center.z, center.z - bounds.maxZ), 0.0);
            best = Math.min(best, dx * dx + dy * dy + dz * dz);
        }
        return best;
    }

    private AABB sectionBounds(SectionKey key) {
        int minY = Math.max(host.getMinY(), key.y() * 16);
        int maxY = Math.min(host.getMaxY(), key.y() * 16 + 16);
        return new AABB(key.x() * 16, minY, key.z() * 16,
                key.x() * 16 + 16, maxY, key.z() * 16 + 16);
    }

    private static int floorSection(double coordinate) {
        return (int) Math.floor(coordinate) >> 4;
    }

    private static boolean finite(AABB bounds) {
        return Double.isFinite(bounds.minX) && Double.isFinite(bounds.minY) && Double.isFinite(bounds.minZ)
                && Double.isFinite(bounds.maxX) && Double.isFinite(bounds.maxY) && Double.isFinite(bounds.maxZ);
    }

    public record Diagnostics(int sections, int boxes, int created, int skipped) {
    }

    private record SectionKey(int x, int y, int z) {
    }

    private static final class Entry {
        private final List<NativePhysicsBody> bodies;
        private final int boxes;
        private int stale;

        private Entry(List<NativePhysicsBody> bodies, int boxes) {
            this.bodies = bodies;
            this.boxes = boxes;
        }
    }
}
