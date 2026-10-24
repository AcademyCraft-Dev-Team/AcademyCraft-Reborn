package org.academy.internal.common.structure.sable;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.academy.api.common.structure.*;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * Optional linkage boundary: queries only already loaded Sable objects. This is not a
 * lifecycle backend, and never allocates plots, starts physics, loads chunks or adds tickets.
 */
public final class SableStructureAccess {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("academy", "sable");
    private static final Map<MinecraftServer, Map<ServerLevel, HostState>> SERVERS = new IdentityHashMap<>();
    private static final double DISTANCE_TOLERANCE = 1.0E-7;
    private static final double POINT_TOLERANCE_SQUARED = 1.0 / (64.0 * 64.0);
    private static final int MAX_READS = 16_384;
    private static final int MAX_CANDIDATES = 256;

    private SableStructureAccess() {}

    public static Optional<StructureHandle> resolve(MinecraftServer server, StructureRef reference) {
        return resolveHandle(server, reference).map(handle -> handle);
    }

    static Optional<SableStructureHandle> resolveHandle(MinecraftServer server, StructureRef reference) {
        requireThread(server);
        Objects.requireNonNull(reference, "reference");
        if (!ID.equals(reference.backend()) || reference.generation() != 0) return Optional.empty();
        for (var host : server.getAllLevels()) {
            if (!host.dimension().identifier().equals(reference.dimension())) continue;
            var state = state(host);
            if (state == null) return Optional.empty();
            var subLevel = state.container.getSubLevel(reference.id());
            if (!(subLevel instanceof ServerSubLevel loaded) || loaded.isRemoved()) return Optional.empty();
            if (SableRestorationIsolation.hidden(loaded)) return Optional.empty();
            var handle = state.handle(loaded);
            return handle.isCurrent() ? Optional.of(handle) : Optional.empty();
        }
        return Optional.empty();
    }

    public static StructurePresentationStatus presentationStatus(MinecraftServer server, StructureRef reference) {
        var handle = resolveHandle(server, reference).orElse(null);
        if (handle == null) return StructurePresentationStatus.NOT_ACTIVE;
        var subLevel = handle.subLevel();
        if (!SableStructureTransforms.unitScale(subLevel.logicalPose())) {
            return StructurePresentationStatus.UNSUPPORTED_CONTENT;
        }
        // Sable supplies its own chunk rendering and interaction protocol. This is a
        // server capability report, not an acknowledgement that an individual client rendered it.
        return subLevel.getPlot().getBoundingBox() == null
                ? StructurePresentationStatus.EMPTY : StructurePresentationStatus.VISIBLE;
    }

    public static Optional<StructureHit> raycast(ServerLevel host, Vec3 worldFrom, Vec3 worldTo) {
        return raycast(host, worldFrom, worldTo, ClipContext.Block.OUTLINE);
    }

    public static Optional<StructureHit> raycast(ServerLevel host, Vec3 worldFrom, Vec3 worldTo, ClipContext.Block shape) {
        var trace = trace(host, worldFrom, worldTo, shape);
        return trace.complete ? Optional.ofNullable(trace.hit) : Optional.empty();
    }

    /** Missing data or unsupported foreground geometry never counts as an unobstructed ray. */
    public static boolean clearSightline(ServerLevel host, Vec3 worldFrom, Vec3 worldTo) {
        var trace = trace(host, worldFrom, worldTo, ClipContext.Block.OUTLINE);
        return trace.complete && trace.hit == null && trace.hostDistance == Double.POSITIVE_INFINITY;
    }

    private static Trace trace(ServerLevel host, Vec3 worldFrom, Vec3 worldTo, ClipContext.Block shape) {
        validateRay(host, worldFrom, worldTo);
        var state = state(host);
        if (state == null) return Trace.UNAVAILABLE;
        try {
            var snapshots = new ArrayList<Snapshot>();
            var poses = new ArrayList<PoseObservation>();
            // The public Sable intersection helper also scans this loaded list, but uses
            // cached global bounds. Recompute from the current pose to include moved bodies.
            for (var subLevel : List.copyOf(state.container.getAllSubLevels())) {
                if (subLevel.isRemoved() || subLevel.getLevel() != host
                        || state.container.getSubLevel(subLevel.getUniqueId()) != subLevel) continue;
                poses.add(new PoseObservation(subLevel, new Pose3d(subLevel.logicalPose())));
                var bounds = subLevel.getPlot().getBoundingBox();
                if (bounds == null) continue;
                var box = new AABB(bounds.minX(), bounds.minY(), bounds.minZ(),
                        bounds.maxX() + 1.0, bounds.maxY() + 1.0, bounds.maxZ() + 1.0);
                if (!SableStructureTransforms.unitScale(subLevel.logicalPose())) {
                    if (intersects(SableStructureTransforms.worldBounds(subLevel.logicalPose(), box),
                            worldFrom, worldTo)) throw new UnavailableRay();
                    continue;
                }
                var handle = state.handle(subLevel);
                var pose = handle.pose();
                var origin = Vec3.atLowerCornerOf(handle.plotOrigin());
                var from = pose.worldToLocalPoint(worldFrom).add(origin);
                var to = pose.worldToLocalPoint(worldTo).add(origin);
                if (!intersects(box, from, to)) continue;
                if (snapshots.size() >= MAX_CANDIDATES) throw new UnavailableRay();
                snapshots.add(new Snapshot(handle, pose, handle.geometryRevision(), from, to));
            }
            // Even with no structure candidate, a complete sightline needs loaded host data.
            long epoch = state.epoch;
            var observations = new Observations(host, state.container);
            var hostHit = new LoadedGetter(observations, null).clip(context(worldFrom, worldTo, shape));
            double hostDistance = hostHit.getType() == HitResult.Type.MISS ? Double.POSITIVE_INFINITY
                    : worldFrom.distanceTo(hostHit.getLocation());
            StructureHit nearest = null;
            double nearestDistance = Double.POSITIVE_INFINITY;
            for (var snapshot : snapshots) {
                var hit = new LoadedGetter(observations, snapshot.handle).clip(context(snapshot.from, snapshot.to, shape));
                if (hit.getType() == HitResult.Type.MISS) continue;
                var localPoint = hit.getLocation().subtract(Vec3.atLowerCornerOf(snapshot.handle.plotOrigin()));
                var worldPoint = snapshot.pose.localToWorldPoint(localPoint);
                double distance = worldFrom.distanceTo(worldPoint);
                if (distance >= hostDistance - DISTANCE_TOLERANCE) continue;
                var reference = snapshot.handle.reference();
                if (nearest != null && (distance > nearestDistance + DISTANCE_TOLERANCE
                        || Math.abs(distance - nearestDistance) <= DISTANCE_TOLERANCE
                        && reference.id().compareTo(nearest.block().structure().id()) >= 0)) continue;
                nearest = new StructureHit(new SpaceBlockRef(reference,
                        hit.getBlockPos().subtract(snapshot.handle.plotOrigin())), snapshot.handle.runtimeId(),
                        snapshot.revision, snapshot.pose, hit.getDirection(), localPoint, worldPoint, hit.isInside());
                nearestDistance = distance;
            }
            if (state.epoch != epoch || !observations.stillValid()) return Trace.UNAVAILABLE;
            for (var pose : poses) if (!pose.current(state.container)) return Trace.UNAVAILABLE;
            for (var snapshot : snapshots) if (!snapshot.current()) return Trace.UNAVAILABLE;
            return new Trace(true, nearest, hostDistance);
        } catch (UnavailableRay unavailable) {
            return Trace.UNAVAILABLE;
        }
    }

    private record Trace(boolean complete, @Nullable StructureHit hit, double hostDistance) {
        private static final Trace UNAVAILABLE = new Trace(false, null, Double.POSITIVE_INFINITY);
    }

    public static StructureHitValidation validate(ServerLevel host, Vec3 trustedWorldFrom,
                                                  Vec3 trustedWorldTo, StructureHit proposed) {
        return validate(host, trustedWorldFrom, trustedWorldTo, proposed, ClipContext.Block.OUTLINE);
    }

    public static StructureHitValidation validate(ServerLevel host, Vec3 trustedWorldFrom,
                                                  Vec3 trustedWorldTo, StructureHit proposed,
                                                  ClipContext.Block shape) {
        validateRay(host, trustedWorldFrom, trustedWorldTo);
        Objects.requireNonNull(proposed, "proposed");
        var reference = proposed.block().structure();
        if (!reference.dimension().equals(host.dimension().identifier())) {
            return failure(StructureHitValidation.Status.NOT_AVAILABLE);
        }
        var handle = resolveHandle(host.getServer(), reference).orElse(null);
        if (handle == null || !SableStructureTransforms.unitScale(handle.subLevel().logicalPose())) {
            return failure(StructureHitValidation.Status.NOT_AVAILABLE);
        }
        if (!handle.runtimeId().equals(proposed.runtimeId())
                || handle.geometryRevision() != proposed.geometryRevision()
                || !SableStructureTransforms.equivalentPose(handle.pose(), proposed.pose())) {
            return failure(StructureHitValidation.Status.STALE);
        }
        var current = raycast(host, trustedWorldFrom, trustedWorldTo, shape).orElse(null);
        if (current == null || !current.block().equals(proposed.block())) {
            return failure(StructureHitValidation.Status.NOT_VISIBLE);
        }
        if (!current.runtimeId().equals(proposed.runtimeId())
                || current.geometryRevision() != proposed.geometryRevision()
                || !SableStructureTransforms.equivalentPose(current.pose(), proposed.pose())) {
            return failure(StructureHitValidation.Status.STALE);
        }
        if (current.localFace() != proposed.localFace() || current.inside() != proposed.inside()
                || current.localPoint().distanceToSqr(proposed.localPoint()) > POINT_TOLERANCE_SQUARED
                || current.worldPoint().distanceToSqr(proposed.worldPoint()) > POINT_TOLERANCE_SQUARED) {
            return failure(StructureHitValidation.Status.NOT_VISIBLE);
        }
        return StructureHitValidation.valid(current);
    }

    /** Called by the optional common block-change route, after an actual palette change. */
    public static void onBlockChanged(ServerLevel host, BlockPos position) {
        requireThread(host.getServer());
        var hosts = SERVERS.get(host.getServer());
        var state = hosts == null ? null : hosts.get(host);
        if (state == null) return;
        state.epoch = Math.incrementExact(state.epoch);
        var plot = state.container.getPlot(new ChunkPos(position.getX() >> 4, position.getZ() >> 4));
        if (plot == null) return;
        var handle = state.handles.get(plot.getSubLevel().getUniqueId());
        if (handle != null && handle.isCurrent()) handle.contentChanged();
    }

    public static void shutdown(MinecraftServer server) {
        requireThread(server);
        var hosts = SERVERS.remove(server);
        if (hosts != null) hosts.values().forEach(HostState::invalidate);
        SableRestorationIsolation.shutdown(server);
    }

    static void invalidateActivation(ServerLevel host, UUID id) {
        var hosts = SERVERS.get(host.getServer());
        var state = hosts == null ? null : hosts.get(host);
        if (state == null) return;
        var previous = state.handles.remove(id);
        if (previous != null) previous.invalidate();
    }

    /**
     * Eagerly installs this host's observer so the lazy first-access path stays a fallback.
     * Registration always runs through {@link SableContainerGuard}, never inside container
     * tick iteration.
     */
    public static void observe(ServerLevel host) {
        requireThread(host.getServer());
        state(host);
    }

    static void requireThread(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        if (!server.isSameThread()) throw new IllegalStateException("Sable structure access requires the server thread");
    }

    private static @Nullable HostState state(ServerLevel host) {
        requireThread(host.getServer());
        if (host.getServer().getLevel(host.dimension()) != host) return null;
        var container = SubLevelContainer.getContainer(host);
        if (container == null) return null;
        var hosts = SERVERS.computeIfAbsent(host.getServer(), ignored -> new IdentityHashMap<>());
        var state = hosts.get(host);
        if (state != null && state.container != container) { state.invalidate(); state = null; }
        if (state == null) {
            var created = new HostState(host, container);
            hosts.put(host, created);
            SableContainerGuard.containerMutation("sable.observer.add", () -> container.addObserver(created));
            state = created;
        }
        return state;
    }

    private static StructureHitValidation failure(StructureHitValidation.Status status) {
        return StructureHitValidation.failure(status);
    }

    private static boolean intersects(AABB box, Vec3 from, Vec3 to) {
        return box.contains(from) || box.clip(from, to).isPresent();
    }

    private static ClipContext context(Vec3 from, Vec3 to, ClipContext.Block shape) {
        return new ClipContext(from, to, shape, ClipContext.Fluid.NONE, CollisionContext.empty());
    }

    private static void validateRay(ServerLevel host, Vec3 from, Vec3 to) {
        Objects.requireNonNull(host, "host");
        requireThread(host.getServer());
        if (host.getServer().getLevel(host.dimension()) != host) {
            throw new IllegalArgumentException("Sable rays require a live host level");
        }
        finite(from);
        finite(to);
        double length = from.distanceTo(to);
        if (!Double.isFinite(length) || length < 1.0E-6 || length > StructureQueries.MAX_RAY_LENGTH) {
            throw new IllegalArgumentException("Structure ray length must be positive and at most 256 blocks");
        }
        var container = SubLevelContainer.getContainer(host);
        if (container != null && (container.getPlot(new ChunkPos(net.minecraft.util.Mth.floor(from.x) >> 4, net.minecraft.util.Mth.floor(from.z) >> 4)) != null
                || container.getPlot(new ChunkPos(net.minecraft.util.Mth.floor(to.x) >> 4, net.minecraft.util.Mth.floor(to.z) >> 4)) != null)) {
            throw new IllegalArgumentException("Structure rays require world coordinates, not private plot addresses");
        }
    }

    private static void finite(Vec3 point) {
        Objects.requireNonNull(point, "point");
        double limit = Integer.MAX_VALUE - StructureQueries.MAX_RAY_LENGTH;
        if (!Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)
                || Math.abs(point.x) > limit || Math.abs(point.y) > limit || Math.abs(point.z) > limit) {
            throw new IllegalArgumentException("Structure ray coordinates must be finite and representable as blocks");
        }
    }

    private static final class HostState implements SubLevelObserver {
        private final ServerLevel host;
        private final ServerSubLevelContainer container;
        private final Map<UUID, SableStructureHandle> handles = new HashMap<>();
        private long epoch;

        private HostState(ServerLevel host, ServerSubLevelContainer container) {
            this.host = host;
            this.container = container;
        }

        private SableStructureHandle handle(ServerSubLevel subLevel) {
            var handle = handles.get(subLevel.getUniqueId());
            if (handle == null || !handle.isCurrent()) {
                if (handle != null) handle.invalidate();
                handle = new SableStructureHandle(host, subLevel);
                handles.put(subLevel.getUniqueId(), handle);
            }
            return handle;
        }

        @Override public void onSubLevelAdded(SubLevel subLevel) {
            requireThread(host.getServer());
            epoch = Math.incrementExact(epoch);
        }
        @Override public void onSubLevelRemoved(SubLevel subLevel, SubLevelRemovalReason reason) {
            requireThread(host.getServer());
            epoch = Math.incrementExact(epoch);
            var handle = handles.get(subLevel.getUniqueId());
            if (handle != null && handle.refersTo(subLevel)) {
                handle.invalidate();
                handles.remove(subLevel.getUniqueId());
            }
        }

        @Override public void tick(SubLevelContainer ignored) {
            requireThread(host.getServer());
            handles.values().removeIf(handle -> {
                if (handle.isCurrent()) return false;
                handle.invalidate();
                return true;
            });
        }

        private void invalidate() { handles.values().forEach(SableStructureHandle::invalidate); handles.clear(); }
    }

    private record Snapshot(SableStructureHandle handle, StructurePose pose, long revision, Vec3 from, Vec3 to) {
        boolean current() {
            return handle.isCurrent() && SableStructureTransforms.unitScale(handle.subLevel().logicalPose())
                    && handle.geometryRevision() == revision && handle.pose().equals(pose);
        }
    }

    private record PoseObservation(ServerSubLevel subLevel, Pose3d pose) {
        boolean current(ServerSubLevelContainer container) {
            var current = subLevel.logicalPose();
            return !subLevel.isRemoved() && container.getSubLevel(subLevel.getUniqueId()) == subLevel
                    && pose.position().equals(current.position()) && pose.orientation().equals(current.orientation())
                    && pose.rotationPoint().equals(current.rotationPoint()) && pose.scale().equals(current.scale());
        }
    }

    private static final class UnavailableRay extends RuntimeException {
        private UnavailableRay() { super(null, null, false, false); }
    }

    /** Plain BlockGetter avoids Sable's Level.clip projection and its loading block reads. */
    private record LoadedGetter(Observations observations, @Nullable SableStructureHandle handle) implements BlockGetter {
        @Override public BlockState getBlockState(BlockPos position) { return observations.state(handle, position); }
        @Override public @Nullable BlockEntity getBlockEntity(BlockPos position) { return observations.entity(handle, position); }
        @Override public FluidState getFluidState(BlockPos position) { return getBlockState(position).getFluidState(); }
        @Override public int getHeight() { return observations.host.getHeight(); }
        @Override public int getMinY() { return observations.host.getMinY(); }
    }

    private static final class Observations {
        private final ServerLevel host;
        private final ServerSubLevelContainer container;
        private final Map<Cell, BlockState> states = new HashMap<>();
        private final Map<Cell, @Nullable BlockEntity> entities = new HashMap<>();
        private final Map<Cell, @Nullable LevelChunk> chunks = new HashMap<>();
        private int reads;

        private Observations(ServerLevel host, ServerSubLevelContainer container) { this.host = host; this.container = container; }

        private @Nullable LevelChunk lookup(@Nullable SableStructureHandle handle, BlockPos position) {
            if (host.isOutsideBuildHeight(position)) return null;
            if (handle == null) {
                if (container.getPlot(new ChunkPos(position.getX() >> 4, position.getZ() >> 4)) != null) throw new UnavailableRay();
                var chunk = host.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
                if (chunk == null) throw new UnavailableRay();
                return chunk;
            }
            if (!handle.isCurrent()) throw new UnavailableRay();
            var plot = handle.subLevel().getPlot();
            if (!plot.contains(position.getX(), position.getZ())) return null;
            var holder = plot.getChunkHolder(plot.toLocal(new ChunkPos(position.getX() >> 4, position.getZ() >> 4)));
            var bounds = plot.getBoundingBox();
            if (holder == null && bounds != null && position.getX() >= bounds.minX() && position.getX() <= bounds.maxX()
                    && position.getY() >= bounds.minY() && position.getY() <= bounds.maxY()
                    && position.getZ() >= bounds.minZ() && position.getZ() <= bounds.maxZ()) {
                throw new UnavailableRay();
            }
            return holder == null ? null : holder.getChunk();
        }

        private @Nullable LevelChunk chunk(Cell cell) {
            if (++reads > MAX_READS) throw new UnavailableRay();
            var chunk = lookup(cell.handle, cell.position);
            if (chunks.containsKey(cell) && chunks.get(cell) != chunk) throw new UnavailableRay();
            chunks.put(cell, chunk);
            return chunk;
        }

        private BlockState state(@Nullable SableStructureHandle handle, BlockPos position) {
            var cell = new Cell(handle, position.immutable());
            var chunk = chunk(cell);
            var state = chunk == null ? Blocks.AIR.defaultBlockState() : chunk.getBlockState(position);
            var previous = states.putIfAbsent(cell, state);
            if (previous != null && previous != state) throw new UnavailableRay();
            return state;
        }

        private @Nullable BlockEntity entity(@Nullable SableStructureHandle handle, BlockPos position) {
            var cell = new Cell(handle, position.immutable());
            state(handle, position);
            var chunk = chunks.get(cell);
            var entity = chunk == null ? null : chunk.getBlockEntities().get(position);
            if (entities.containsKey(cell) && entities.get(cell) != entity) throw new UnavailableRay();
            entities.put(cell, entity);
            return entity;
        }

        private boolean stillValid() {
            if (host.getServer().getLevel(host.dimension()) != host || SubLevelContainer.getContainer(host) != container) return false;
            for (var entry : chunks.entrySet()) if (lookup(entry.getKey().handle, entry.getKey().position) != entry.getValue()) return false;
            for (var entry : states.entrySet()) {
                var chunk = chunks.get(entry.getKey());
                if (chunk != null && chunk.getBlockState(entry.getKey().position) != entry.getValue()) return false;
            }
            for (var entry : entities.entrySet()) {
                var chunk = chunks.get(entry.getKey());
                if (chunk != null && chunk.getBlockEntities().get(entry.getKey().position) != entry.getValue()) return false;
            }
            return true;
        }
    }

    private record Cell(@Nullable SableStructureHandle handle, BlockPos position) {}
}
