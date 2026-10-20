package org.academy.internal.common.sublevel;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import org.academy.AcademyCraft;
import org.academy.internal.common.structure.physics.BodyType;
import org.academy.internal.common.structure.physics.NativePhysicsBody;
import org.academy.internal.common.structure.physics.NativePhysicsWorld;
import org.academy.internal.common.structure.physics.PhysicsPose;
import org.academy.internal.common.structure.physics.PhysicsQuaternion;
import org.academy.internal.common.structure.physics.PhysicsVector;
import org.academy.internal.common.structure.space.NativeStructureTickets;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Bounded ChunkSection terrain cache for one shared physics universe. Only sections around
 * active SubLevels are materialized; irrelevant sections are released before reserving coverage.
 */
public final class TerrainColliderCache implements AutoCloseable {
    public static final int DEFAULT_MAX_SECTIONS = 64;
    public static final int DEFAULT_MAX_BOXES = 28_000;
    private static final int OPTIONAL_PREWARM_SECTIONS = 16;
    private static final int BOXES_PER_SECTION = 256;
    static final int MAX_CANDIDATE_SECTIONS = 512;
    static final int MAX_REQUIRED_CHUNKS = 64;
    private static final int MAX_SYNC_CHUNK_LOADS_PER_TICK = 4;

    private final ServerLevel host;
    private final NativePhysicsWorld world;
    private final int maxSections;
    private final int maxBoxes;
    private final Map<SectionKey, Entry> sections = new LinkedHashMap<>();
    private final Map<SectionKey, LevelChunk> emptySections = new LinkedHashMap<>();
    private final Map<SectionKey, Integer> retryAt = new LinkedHashMap<>();
    private final java.util.Set<SectionKey> invalidated = new LinkedHashSet<>();
    private final java.util.Set<ChunkPos> collisionTickets = new LinkedHashSet<>();
    private final CoveragePlanner coveragePlanner = new CoveragePlanner();
    private CoveragePlan appliedPlan;
    private int chunkLoadTick = Integer.MIN_VALUE;
    private int syncChunkLoads;
    private int updates;
    private int skipped;
    private boolean coverageComplete;
    private String coverageFailure = "not_sampled";

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

    /** Defers native changes until the next terrain pass, before physics advances. */
    public void onBlockChanged(BlockPos position) {
        Objects.requireNonNull(position, "position");
        // Neighbor-dependent collision shapes can cross a section boundary (fences, walls).
        for (int x = (position.getX() - 1) >> 4; x <= (position.getX() + 1) >> 4; x++) {
            for (int y = (position.getY() - 1) >> 4; y <= (position.getY() + 1) >> 4; y++) {
                for (int z = (position.getZ() - 1) >> 4; z <= (position.getZ() + 1) >> 4; z++) {
                    var key = new SectionKey(x, y, z);
                    if (sections.containsKey(key) || emptySections.containsKey(key)) invalidated.add(key);
                    emptySections.remove(key);
                    // A newly placed block must not wait out the empty-section backoff.
                    retryAt.remove(key);
                }
            }
        }
    }

    /** Materializes/removes sections around active world bounds; safe to call once per server tick. */
    public Diagnostics update(Collection<AABB> activeBounds) {
        Objects.requireNonNull(activeBounds, "activeBounds");
        var plan = coveragePlanner.plan(activeBounds, host.getMinY(), host.getMaxY());
        // Movement inside the same covered sections neither changes terrain nor its tickets.
        // Block edits and chunk replacement still invalidate this fast path immediately.
        if (plan == appliedPlan && !needsUpdate()) return diagnostics();
        updates = Math.incrementExact(updates);
        for (var key : List.copyOf(invalidated)) remove(key);
        invalidated.clear();
        for (var entry : List.copyOf(sections.entrySet())) {
            var key = entry.getKey();
            if (host.getChunkSource().getChunkNow(key.x(), key.z()) != entry.getValue().chunk) remove(key);
        }
        emptySections.entrySet().removeIf(entry -> host.getChunkSource()
                .getChunkNow(entry.getKey().x(), entry.getKey().z()) != entry.getValue());
        var chunkPlan = planRequiredChunks(plan.required());
        updateRequiredChunks(chunkPlan, plan.bounded());
        var required = new LinkedHashSet<>(plan.required());
        var needed = new LinkedHashSet<>(required);
        needed.addAll(plan.nearby());
        emptySections.keySet().retainAll(needed);
        retryAt.keySet().retainAll(needed);
        for (var key : List.copyOf(sections.keySet())) {
            if (!needed.contains(key)) remove(key);
        }
        coverageComplete = plan.bounded() && chunkPlan.bounded();
        coverageFailure = !plan.bounded() ? "required_section_limit"
                : !chunkPlan.bounded() ? "required_chunk_limit" : "";
        int created = 0;
        // Required solid sections always take precedence over speculative halo colliders.
        // Empty sections are receipts, not native bodies, and never consume the collider cap.
        for (var key : needed) {
            if (sections.containsKey(key) || emptySections.containsKey(key)) continue;
            boolean mandatory = required.contains(key);
            // The larger capacity is reserved for required coverage. Speculative prewarming
            // retains the original total-section ceiling, even when many bodies need more.
            if (!mandatory && (sections.size() >= Math.min(maxSections, OPTIONAL_PREWARM_SECTIONS)
                    || retryAt.getOrDefault(key, 0) > updates)) continue;
            var result = add(key, mandatory, required);
            if (result == CaptureResult.SOLID || result == CaptureResult.EMPTY) {
                retryAt.remove(key);
                if (result == CaptureResult.SOLID) created++;
            } else {
                retryAt.put(key, updates + 200);
                if (mandatory) {
                    coverageComplete = false;
                    if (coverageFailure.isEmpty()) coverageFailure = result + ":" + key;
                }
            }
        }
        int boxes = sections.values().stream().mapToInt(entry -> entry.boxes).sum();
        appliedPlan = plan;
        return new Diagnostics(sections.size(), boxes, created, skipped);
    }

    public Diagnostics diagnostics() {
        int boxes = sections.values().stream().mapToInt(entry -> entry.boxes).sum();
        return new Diagnostics(sections.size(), boxes, 0, skipped);
    }

    public boolean coverageComplete() { return coverageComplete; }

    public String coverageFailure() { return coverageFailure; }

    public int collisionTicketCount() { return collisionTickets.size(); }

    /** Counts explicit synchronous loads, not asynchronous work initiated by FULL tickets. */
    public int synchronousChunkLoadsThisTick() {
        return chunkLoadTick == host.getServer().getTickCount() ? syncChunkLoads : 0;
    }

    /** Cheap readiness check also notices chunk reloads while every body is asleep. */
    public boolean needsUpdate() {
        if (!coverageComplete || !invalidated.isEmpty()) return true;
        for (var entry : sections.entrySet()) {
            if (host.getChunkSource().getChunkNow(entry.getKey().x(), entry.getKey().z()) != entry.getValue().chunk) return true;
        }
        for (var entry : emptySections.entrySet()) {
            if (host.getChunkSource().getChunkNow(entry.getKey().x(), entry.getKey().z()) != entry.getValue()) return true;
        }
        return false;
    }

    public void clear() {
        for (var key : List.copyOf(sections.keySet())) remove(key);
        retryAt.clear();
        invalidated.clear();
        emptySections.clear();
        coverageComplete = false;
        appliedPlan = null;
        coveragePlanner.clear();
        coverageFailure = "cleared";
        releaseCollisionTickets();
    }

    @Override
    public void close() {
        clear();
    }

    private CaptureResult add(SectionKey key, boolean mandatory, java.util.Set<SectionKey> required) {
        var bodies = new ArrayList<NativePhysicsBody>();
        try {
            // Required chunks were ticketed and loaded under the per-tick cap. Optional halo
            // sections may use resident chunks, but never cause additional chunk loading.
            var chunk = host.getChunkSource().getChunkNow(key.x(), key.z());
            if (chunk == null) { skipped++; return CaptureResult.UNLOADED; }
            if (chunk.getSection(chunk.getSectionIndex(key.y() * 16)).hasOnlyAir()) {
                emptySections.put(key, chunk);
                return CaptureResult.EMPTY;
            }
            var geometry = TerrainSectionGeometry.capture(host, key.x(), key.y(), key.z());
            if (geometry.boxes().isEmpty()) {
                emptySections.put(key, chunk);
                return CaptureResult.EMPTY;
            }
            int currentBoxes = sections.values().stream().mapToInt(entry -> entry.boxes).sum();
            if (mandatory) {
                for (var optional : List.copyOf(sections.keySet())) {
                    if (sections.size() < maxSections && geometry.boxes().size() <= maxBoxes - currentBoxes) break;
                    if (required.contains(optional)) continue;
                    currentBoxes -= sections.get(optional).boxes;
                    remove(optional);
                }
            }
            if (sections.size() >= maxSections || geometry.boxes().size() > maxBoxes - currentBoxes) {
                skipped++;
                AcademyCraft.LOGGER.debug("Skipping terrain section {}: terrain box budget {}/{}",
                        key, currentBoxes, maxBoxes);
                return CaptureResult.BUDGET;
            }
            var origin = world.origin();
            var pose = new PhysicsPose(new PhysicsVector(key.x() * 16 - origin.x(),
                    key.y() * 16 - origin.y(), key.z() * 16 - origin.z()), PhysicsQuaternion.IDENTITY);
            for (int start = 0; start < geometry.boxes().size(); start += BOXES_PER_SECTION) {
                var batch = List.copyOf(geometry.boxes().subList(start,
                        Math.min(geometry.boxes().size(), start + BOXES_PER_SECTION)));
                bodies.add(world.createBody(BodyType.FIXED, pose, batch, 0.0, 0.6, 0.0, false));
            }
            sections.put(key, new Entry(chunk, List.copyOf(bodies), geometry.boxes().size()));
            AcademyCraft.LOGGER.debug("Terrain section added {} boxes={} bodies={}", key,
                    geometry.boxes().size(), bodies.size());
            return CaptureResult.SOLID;
        } catch (RuntimeException | AssertionError failure) {
            for (var body : bodies) {
                try { body.close(); }
                catch (RuntimeException closeFailure) { failure.addSuppressed(closeFailure); }
            }
            skipped++;
            AcademyCraft.LOGGER.debug("Skipping terrain section {}: {}", key, failure.toString());
            return CaptureResult.FAILED;
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

    private static double distanceToBounds(SectionKey key, Collection<AABB> activeBounds) {
        double centerX = key.x() * 16.0 + 8, centerY = key.y() * 16.0 + 8, centerZ = key.z() * 16.0 + 8;
        double best = Double.POSITIVE_INFINITY;
        for (var bounds : activeBounds) {
            if (bounds == null || !finite(bounds)) continue;
            double dx = Math.max(Math.max(bounds.minX - centerX, centerX - bounds.maxX), 0.0);
            double dy = Math.max(Math.max(bounds.minY - centerY, centerY - bounds.maxY), 0.0);
            double dz = Math.max(Math.max(bounds.minZ - centerZ, centerZ - bounds.maxZ), 0.0);
            best = Math.min(best, dx * dx + dy * dy + dz * dz);
        }
        return best;
    }

    private void updateRequiredChunks(ChunkPlan plan, boolean sectionsBounded) {
        var wanted = new LinkedHashSet<>(plan.required());
        for (var position : List.copyOf(collisionTickets)) {
            if (!wanted.contains(position)) {
                NativeStructureTickets.releaseCollision(host, position);
                collisionTickets.remove(position);
            }
        }
        for (var position : wanted) {
            if (!collisionTickets.contains(position)) {
                NativeStructureTickets.acquireCollision(host, position);
                collisionTickets.add(position);
            }
        }
        int tick = host.getServer().getTickCount();
        if (chunkLoadTick != tick) {
            chunkLoadTick = tick;
            syncChunkLoads = 0;
        }
        // An oversized plan cannot be completed, so do not synchronously generate part of it.
        if (!sectionsBounded || !plan.bounded()) return;
        for (var position : wanted) {
            if (syncChunkLoads >= MAX_SYNC_CHUNK_LOADS_PER_TICK) break;
            if (host.getChunkSource().getChunkNow(position.x(), position.z()) != null) continue;
            syncChunkLoads++;
            host.getChunk(position.x(), position.z());
        }
    }

    private void releaseCollisionTickets() {
        RuntimeException failure = null;
        for (var position : List.copyOf(collisionTickets)) {
            try {
                NativeStructureTickets.releaseCollision(host, position);
                collisionTickets.remove(position);
            } catch (RuntimeException current) {
                if (failure == null) failure = current;
                else failure.addSuppressed(current);
            }
        }
        if (failure != null) throw failure;
    }

    /** Vertical sections share one FULL ticket; speculative halo and inter-body gaps do not. */
    static ChunkPlan planRequiredChunks(Collection<SectionKey> requiredSections) {
        var chunks = new LinkedHashSet<ChunkPos>();
        boolean bounded = true;
        for (var section : requiredSections) {
            var position = new ChunkPos(section.x(), section.z());
            if (chunks.contains(position)) continue;
            if (chunks.size() >= MAX_REQUIRED_CHUNKS) {
                bounded = false;
                continue;
            }
            chunks.add(position);
        }
        return new ChunkPlan(List.copyOf(chunks), bounded);
    }

    /** Plans each body's local neighbourhood; never fills the gap between separated bodies. */
    static CoveragePlan planCoverage(Collection<AABB> activeBounds, int minHeight, int maxHeight) {
        var relevant = new ArrayList<AABB>();
        var required = new LinkedHashSet<SectionKey>();
        boolean bounded = true;
        for (var raw : activeBounds) {
            if (raw == null || !finite(raw)) { bounded = false; continue; }
            if (raw.maxY <= minHeight || raw.minY >= maxHeight) continue;
            var bounds = raw.inflate(0.1);
            relevant.add(bounds);
            int minX = floorSection(bounds.minX), maxX = floorSection(bounds.maxX);
            int minY = Math.max(floorSection(minHeight), floorSection(bounds.minY));
            int maxY = Math.min(floorSection(maxHeight - 1.0), floorSection(bounds.maxY));
            int minZ = floorSection(bounds.minZ), maxZ = floorSection(bounds.maxZ);
            long nx = (long) maxX - minX + 1, ny = (long) maxY - minY + 1, nz = (long) maxZ - minZ + 1;
            if (nx <= 0 || ny <= 0 || nz <= 0 || nx > MAX_CANDIDATE_SECTIONS || ny > MAX_CANDIDATE_SECTIONS
                    || nz > MAX_CANDIDATE_SECTIONS || nx * ny * nz > MAX_CANDIDATE_SECTIONS) {
                bounded = false;
                continue;
            }
            for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) for (int z = minZ; z <= maxZ; z++) {
                var key = new SectionKey(x, y, z);
                if (required.contains(key)) continue;
                if (required.size() >= MAX_CANDIDATE_SECTIONS) { bounded = false; break; }
                required.add(key);
            }
        }
        var nearby = new LinkedHashSet<SectionKey>();
        halo:
        for (var key : required) for (int x = key.x() - 1; x <= key.x() + 1; x++) {
            for (int y = Math.max(minHeight >> 4, key.y() - 1); y <= Math.min((maxHeight - 1) >> 4, key.y() + 1); y++) {
                for (int z = key.z() - 1; z <= key.z() + 1; z++) {
                    if (required.size() + nearby.size() >= MAX_CANDIDATE_SECTIONS) break halo;
                    var candidate = new SectionKey(x, y, z);
                    if (!required.contains(candidate)) nearby.add(candidate);
                }
            }
        }
        var ordered = new ArrayList<>(nearby);
        ordered.sort(java.util.Comparator.comparingDouble(key -> distanceToBounds(key, relevant)));
        return new CoveragePlan(List.copyOf(required), List.copyOf(ordered), bounded);
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

    record SectionKey(int x, int y, int z) {
    }

    record CoveragePlan(List<SectionKey> required, List<SectionKey> nearby, boolean bounded) {}

    record ChunkPlan(List<ChunkPos> required, boolean bounded) {}

    /**
     * The mandatory coverage depends on section coordinates, not sub-block pose changes. Halo
     * ordering is only a prewarm preference, so it can share the same lifetime as that coverage.
     */
    static final class CoveragePlanner {
        private List<SectionRange> footprint;
        private int minHeight;
        private int maxHeight;
        private CoveragePlan cached;

        CoveragePlan plan(Collection<AABB> bounds, int minHeight, int maxHeight) {
            var next = new ArrayList<SectionRange>(bounds.size());
            boolean valid = true;
            for (var raw : bounds) {
                if (raw == null || !finite(raw)) {
                    valid = false;
                    break;
                }
                if (raw.maxY <= minHeight || raw.minY >= maxHeight) continue;
                var padded = raw.inflate(0.1);
                next.add(new SectionRange(floorSection(padded.minX), floorSection(padded.maxX),
                        Math.max(floorSection(minHeight), floorSection(padded.minY)),
                        Math.min(floorSection(maxHeight - 1.0), floorSection(padded.maxY)),
                        floorSection(padded.minZ), floorSection(padded.maxZ)));
            }
            if (valid && cached != null && this.minHeight == minHeight && this.maxHeight == maxHeight
                    && next.equals(footprint)) return cached;
            var result = planCoverage(bounds, minHeight, maxHeight);
            // Invalid input must never reuse a previously complete coverage receipt.
            footprint = valid ? List.copyOf(next) : null;
            cached = valid ? result : null;
            this.minHeight = minHeight;
            this.maxHeight = maxHeight;
            return result;
        }

        void clear() {
            footprint = null;
            cached = null;
        }
    }

    private record SectionRange(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {}

    private enum CaptureResult { SOLID, EMPTY, UNLOADED, BUDGET, FAILED }

    private static final class Entry {
        private final LevelChunk chunk;
        private final List<NativePhysicsBody> bodies;
        private final int boxes;

        private Entry(LevelChunk chunk, List<NativePhysicsBody> bodies, int boxes) {
            this.chunk = chunk;
            this.bodies = bodies;
            this.boxes = boxes;
        }
    }
}
