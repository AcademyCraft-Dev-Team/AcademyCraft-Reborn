package org.academy.internal.common.structure.space;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
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
import org.academy.api.common.structure.SpaceBlockRef;
import org.academy.api.common.structure.StructureContexts;
import org.academy.api.common.structure.StructureHandle;
import org.academy.api.common.structure.StructureHit;
import org.academy.api.common.structure.StructureHitValidation;
import org.academy.api.common.structure.StructureLifecycle;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureQueries;
import org.academy.api.common.structure.StructureRef;
import org.academy.internal.common.structure.NativeStructureRuntime;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Exact selection-shape queries; all addresses exposed to callers remain structure-local. */
public final class NativeStructureQueries {
    private static final double HIT_POINT_TOLERANCE_SQUARED = 1.0 / (64.0 * 64.0);
    private static final double POSE_TOLERANCE = 1.0E-10;
    private static final double DISTANCE_TOLERANCE = 1.0E-7;
    private static final int MAX_HOST_READS = 16_384;
    public static final double MAX_INTERACTION_DISPLACEMENT = 0.75;

    private NativeStructureQueries() {
    }

    public static Optional<StructureHit> raycast(ServerLevel host, Vec3 worldFrom, Vec3 worldTo) {
        return raycast(host, worldFrom, worldTo, null, null, new HostObservations(host));
    }

    public static Optional<StructureHit> raycast(ServerLevel host, Vec3 worldFrom, Vec3 worldTo, ClipContext.Block shape) {
        return observe(host, worldFrom, worldTo, null, null, new HostObservations(host), shape).hit;
    }

    public static boolean clearSightline(ServerLevel host, Vec3 worldFrom, Vec3 worldTo) {
        var observation = observe(host, worldFrom, worldTo, null, null, new HostObservations(host), ClipContext.Block.OUTLINE);
        return observation.complete && observation.hostClear && observation.hit.isEmpty();
    }

    private record RayObservation(boolean complete, boolean hostClear, Optional<StructureHit> hit) {
        private static RayObservation unavailable() { return new RayObservation(false, false, Optional.empty()); }
    }

    private static Optional<StructureHit> raycast(ServerLevel host, Vec3 worldFrom, Vec3 worldTo,
                                                  @Nullable StructureRef rewind, @Nullable StructurePose oldPose,
                                                  HostObservations observations) {
        return observe(host, worldFrom, worldTo, rewind, oldPose, observations, ClipContext.Block.OUTLINE).hit;
    }

    private static RayObservation observe(ServerLevel host, Vec3 worldFrom, Vec3 worldTo,
                                                  @Nullable StructureRef rewind, @Nullable StructurePose oldPose,
                                                  HostObservations observations, ClipContext.Block shape) {
        validateRay(host, worldFrom, worldTo);
        var backend = NativeStructureRuntime.backend(host.getServer());
        try {
            var snapshots = new ArrayList<Snapshot>();
            for (var handle : backend.activeStructures(host)) {
                var currentPose = handle.pose();
                snapshots.add(new Snapshot(handle, handle.reference(), handle.runtimeId(), handle.geometryRevision(),
                        handle.reference().equals(rewind) ? Objects.requireNonNull(oldPose) : currentPose,
                        currentPose, backend.storageLevel(handle.reference()).orElseThrow(UnavailableRay::new)));
            }
            // This getter refuses missing chunks before touching their states or block entities.
            var hostHit = new LoadedBlockGetter(host, false, observations).clip(context(worldFrom, worldTo, shape));
            var hostDistance = hostHit.getType() == HitResult.Type.MISS ? Double.POSITIVE_INFINITY
                    : worldFrom.distanceTo(hostHit.getLocation());
            StructureHit nearest = null;
            var nearestDistance = Double.POSITIVE_INFINITY;
            for (var snapshot : snapshots) {
                var localFrom = snapshot.pose.worldToLocalPoint(worldFrom);
                var localTo = snapshot.pose.worldToLocalPoint(worldTo);
                var bounds = new AABB(NativeStructureDimensions.MIN_COORDINATE, snapshot.level.getMinY(),
                        NativeStructureDimensions.MIN_COORDINATE, NativeStructureDimensions.MAX_COORDINATE_EXCLUSIVE,
                        snapshot.level.getMaxY() + 1.0, NativeStructureDimensions.MAX_COORDINATE_EXCLUSIVE);
                if (!bounds.contains(localFrom) && bounds.clip(localFrom, localTo).isEmpty()) continue;
                var hit = StructureContexts.call(snapshot.handle, ignored ->
                        new LoadedBlockGetter(snapshot.level, true, null).clip(context(localFrom, localTo, shape)));
                if (hit.getType() == HitResult.Type.MISS) continue;
                var worldPoint = snapshot.pose.localToWorldPoint(hit.getLocation());
                var distance = worldFrom.distanceTo(worldPoint);
                // A coincident host face wins; hitting a structure never permits reaching through it.
                if (distance >= hostDistance - DISTANCE_TOLERANCE) continue;
                if (nearest != null && (distance > nearestDistance + DISTANCE_TOLERANCE
                        || Math.abs(distance - nearestDistance) <= DISTANCE_TOLERANCE
                        && snapshot.reference.id().compareTo(nearest.block().structure().id()) >= 0)) continue;
                nearest = new StructureHit(new SpaceBlockRef(snapshot.reference, hit.getBlockPos()),
                        snapshot.runtimeId, snapshot.revision, snapshot.pose, hit.getDirection(),
                        hit.getLocation(), worldPoint, hit.isInside());
                nearestDistance = distance;
            }
            // Shape callbacks can reenter mod code; never return an observation spanning mutations.
            if (!backend.activeStructures(host).equals(snapshots.stream().map(Snapshot::handle).toList())) {
                return RayObservation.unavailable();
            }
            for (var snapshot : snapshots) if (!snapshot.isCurrent()) return RayObservation.unavailable();
            if (!observations.stillValid()) return RayObservation.unavailable();
            return new RayObservation(true, hostHit.getType() == HitResult.Type.MISS, Optional.ofNullable(nearest));
        } catch (UnavailableRay | IllegalStateException unavailable) {
            return RayObservation.unavailable();
        }
    }

    public static StructureHitValidation validate(ServerLevel host, Vec3 trustedWorldFrom,
                                                  Vec3 trustedWorldTo, StructureHit proposed) {
        validateRay(host, trustedWorldFrom, trustedWorldTo);
        Objects.requireNonNull(proposed, "proposed");
        var reference = proposed.block().structure();
        if (!reference.dimension().equals(host.dimension().identifier())) {
            return StructureHitValidation.failure(StructureHitValidation.Status.NOT_AVAILABLE);
        }
        try {
            return validateCurrent(host, trustedWorldFrom, trustedWorldTo, proposed);
        } catch (IllegalStateException unavailable) {
            // An asynchronous storage failure can invalidate a handle between its accessors.
            return StructureHitValidation.failure(StructureHitValidation.Status.NOT_AVAILABLE);
        }
    }

    private static StructureHitValidation validateCurrent(ServerLevel host, Vec3 trustedWorldFrom,
                                                          Vec3 trustedWorldTo, StructureHit proposed) {
        var handle = NativeStructureRuntime.backend(host.getServer()).resolve(proposed.block().structure()).orElse(null);
        if (handle == null || handle.lifecycle() != StructureLifecycle.ACTIVE) {
            return StructureHitValidation.failure(StructureHitValidation.Status.NOT_AVAILABLE);
        }
        if (!handle.runtimeId().equals(proposed.runtimeId())
                || handle.geometryRevision() != proposed.geometryRevision()
                || !equivalentPose(handle.pose(), proposed.pose())) {
            return StructureHitValidation.failure(StructureHitValidation.Status.STALE);
        }
        var current = raycast(host, trustedWorldFrom, trustedWorldTo).orElse(null);
        if (current == null || !current.block().equals(proposed.block())) {
            return StructureHitValidation.failure(StructureHitValidation.Status.NOT_VISIBLE);
        }
        if (!current.runtimeId().equals(proposed.runtimeId())
                || current.geometryRevision() != proposed.geometryRevision()
                || !equivalentPose(current.pose(), proposed.pose())) {
            return StructureHitValidation.failure(StructureHitValidation.Status.STALE);
        }
        if (current.localFace() != proposed.localFace() || current.inside() != proposed.inside()
                || current.localPoint().distanceToSqr(proposed.localPoint()) > HIT_POINT_TOLERANCE_SQUARED
                || current.worldPoint().distanceToSqr(proposed.worldPoint()) > HIT_POINT_TOLERANCE_SQUARED) {
            return StructureHitValidation.failure(StructureHitValidation.Status.NOT_VISIBLE);
        }
        return StructureHitValidation.valid(current);
    }

    /**
     * Player-input reconciliation only. Public strict queries retain their exact-current contract.
     * Only the target's server-published physical pose is rewound; terrain and other structures
     * must be clear in the current world as well. No geometry or actor history comes from packets.
     */
    public static StructureHitValidation validateMoving(ServerLevel host, Vec3 trustedWorldFrom,
                                                         Vec3 trustedWorldTo, StructureHit proposed) {
        validateRay(host, trustedWorldFrom, trustedWorldTo);
        Objects.requireNonNull(proposed, "proposed");
        var runtime = NativeStructureRuntime.backend(host.getServer());
        try {
            var handle = runtime.resolve(proposed.block().structure()).orElse(null);
            if (handle == null || !handle.runtimeId().equals(proposed.runtimeId())
                    || !proposed.block().structure().dimension().equals(host.dimension().identifier())
                    || !runtime.isPresented(handle.reference())) {
                return StructureHitValidation.failure(StructureHitValidation.Status.NOT_AVAILABLE);
            }
            if (handle.geometryRevision() == proposed.geometryRevision() && equivalentPose(handle.pose(), proposed.pose())) {
                return validateCurrent(host, trustedWorldFrom, trustedWorldTo, proposed);
            }
            var previous = runtime.previousPhysicalPose(proposed).orElse(null);
            if (previous == null || !equivalentPose(previous, proposed.pose())
                    || previous.localToWorldPoint(proposed.localPoint()).distanceToSqr(proposed.worldPoint()) > HIT_POINT_TOLERANCE_SQUARED) {
                return StructureHitValidation.failure(StructureHitValidation.Status.STALE);
            }
            // Both legs belong to one observation: callbacks in the current leg cannot alter
            // the previously checked historical leg's host terrain without invalidating it.
            var observations = new HostObservations(host);
            var historyHit = raycast(host, trustedWorldFrom, trustedWorldTo,
                    handle.reference(), previous, observations).orElse(null);
            if (!sameSurface(historyHit, proposed)
                    || historyHit.worldPoint().distanceToSqr(proposed.worldPoint()) > HIT_POINT_TOLERANCE_SQUARED) {
                return StructureHitValidation.failure(StructureHitValidation.Status.NOT_VISIBLE);
            }
            var pose = handle.pose();
            long revision = handle.geometryRevision();
            var target = pose.localToWorldPoint(historyHit.localPoint());
            double reach = trustedWorldFrom.distanceTo(trustedWorldTo);
            double distance = trustedWorldFrom.distanceTo(target);
            if (target.distanceToSqr(historyHit.worldPoint()) > MAX_INTERACTION_DISPLACEMENT * MAX_INTERACTION_DISPLACEMENT
                    || !Double.isFinite(distance) || distance < 1.0E-6 || distance > reach) {
                return StructureHitValidation.failure(StructureHitValidation.Status.NOT_VISIBLE);
            }
            var end = trustedWorldFrom.add(target.subtract(trustedWorldFrom)
                    .scale(Math.min(reach, distance + 1.0E-5) / distance));
            var current = raycast(host, trustedWorldFrom, end, null, null, observations).orElse(null);
            // Preserve the clicked local surface and placement intent, including partial blocks.
            if (!sameSurface(current, historyHit) || current.worldPoint().distanceToSqr(target) > HIT_POINT_TOLERANCE_SQUARED) {
                return StructureHitValidation.failure(StructureHitValidation.Status.NOT_VISIBLE);
            }
            if (handle.geometryRevision() != revision || !handle.pose().equals(pose)
                    || runtime.previousPhysicalPose(proposed).filter(previous::equals).isEmpty()
                    || !observations.stillValid()) {
                return StructureHitValidation.failure(StructureHitValidation.Status.STALE);
            }
            return StructureHitValidation.valid(current);
        } catch (IllegalStateException unavailable) {
            return StructureHitValidation.failure(StructureHitValidation.Status.NOT_AVAILABLE);
        }
    }

    private static boolean sameSurface(@Nullable StructureHit actual, StructureHit expected) {
        return actual != null && actual.block().equals(expected.block()) && actual.runtimeId().equals(expected.runtimeId())
                && actual.localFace() == expected.localFace() && actual.inside() == expected.inside()
                && actual.localPoint().distanceToSqr(expected.localPoint()) <= HIT_POINT_TOLERANCE_SQUARED;
    }

    /** Normalization after wire reconstruction may change the final quaternion bits. */
    static boolean equivalentPose(StructurePose first, StructurePose second) {
        var a = first.worldOrigin();
        var b = second.worldOrigin();
        if (Math.abs(a.x - b.x) > POSE_TOLERANCE || Math.abs(a.y - b.y) > POSE_TOLERANCE
                || Math.abs(a.z - b.z) > POSE_TOLERANCE) return false;
        var ar = first.rotation();
        var br = second.rotation();
        // q and -q describe the same orientation. Compare components rather than 1 - dot,
        // which loses precision near identity and admits much larger angular differences.
        return quaternionNear(ar.x, ar.y, ar.z, ar.w, br.x, br.y, br.z, br.w)
                || quaternionNear(ar.x, ar.y, ar.z, ar.w, -br.x, -br.y, -br.z, -br.w);
    }

    private static boolean quaternionNear(double ax, double ay, double az, double aw,
                                          double bx, double by, double bz, double bw) {
        return Math.abs(ax - bx) <= POSE_TOLERANCE && Math.abs(ay - by) <= POSE_TOLERANCE
                && Math.abs(az - bz) <= POSE_TOLERANCE && Math.abs(aw - bw) <= POSE_TOLERANCE;
    }

    private static ClipContext context(Vec3 from, Vec3 to, ClipContext.Block shape) {
        return new ClipContext(from, to, shape, ClipContext.Fluid.NONE, CollisionContext.empty());
    }

    private static void validateRay(ServerLevel host, Vec3 from, Vec3 to) {
        Objects.requireNonNull(host, "host");
        if (!host.getServer().isSameThread()) throw new IllegalStateException("Structure rays require the server thread");
        if (host.getServer().getLevel(host.dimension()) != host
                || NativeStructureDimensions.isStorageLevel(host.dimension())) {
            throw new IllegalArgumentException("Structure rays require a live host level");
        }
        requireFinite(from);
        requireFinite(to);
        var length = from.distanceTo(to);
        if (!Double.isFinite(length) || length < 1.0E-6 || length > StructureQueries.MAX_RAY_LENGTH) {
            throw new IllegalArgumentException("Structure ray length must be positive and at most 256 blocks");
        }
    }

    private static void requireFinite(Vec3 point) {
        Objects.requireNonNull(point, "point");
        // Vanilla's DDA floors into BlockPos integers; reject coordinates that would saturate them.
        if (!Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)
                || Math.abs(point.x) > Integer.MAX_VALUE - StructureQueries.MAX_RAY_LENGTH
                || Math.abs(point.y) > Integer.MAX_VALUE - StructureQueries.MAX_RAY_LENGTH
                || Math.abs(point.z) > Integer.MAX_VALUE - StructureQueries.MAX_RAY_LENGTH) {
            throw new IllegalArgumentException("Structure ray coordinates must be finite and representable as blocks");
        }
    }

    private record Snapshot(StructureHandle handle, StructureRef reference, UUID runtimeId,
                            long revision, StructurePose pose, StructurePose observedPose, ServerLevel level) {
        private boolean isCurrent() {
            return handle.lifecycle() == StructureLifecycle.ACTIVE
                    && handle.reference().equals(reference) && handle.runtimeId().equals(runtimeId)
                    && handle.geometryRevision() == revision && handle.pose().equals(observedPose);
        }
    }

    private static final class UnavailableRay extends RuntimeException {
        private UnavailableRay() {
            super(null, null, false, false);
        }
    }

    /** Excludes the storage boundary barriers and refuses every unavailable chunk lookup. */
    private record LoadedBlockGetter(ServerLevel level, boolean structure,
                                     @Nullable HostObservations observations) implements BlockGetter {
        private boolean outside(BlockPos position) {
            return level.isOutsideBuildHeight(position) || structure && !NativeStructureDimensions.contains(position);
        }

        private void requireLoaded(BlockPos position) {
            if (!level.hasChunkAt(position)) throw new UnavailableRay();
        }

        @Override
        public @Nullable BlockEntity getBlockEntity(BlockPos position) {
            if (outside(position)) return null;
            if (observations != null) return observations.blockEntity(position);
            requireLoaded(position);
            return level.getBlockEntity(position);
        }

        @Override
        public BlockState getBlockState(BlockPos position) {
            if (outside(position)) return Blocks.AIR.defaultBlockState();
            if (observations != null) return observations.state(position);
            requireLoaded(position);
            return level.getBlockState(position);
        }

        @Override
        public FluidState getFluidState(BlockPos position) {
            return getBlockState(position).getFluidState();
        }

        @Override public int getHeight() { return level.getHeight(); }
        @Override public int getMinY() { return level.getMinY(); }
    }

    /** Bounded, loaded-only host observations; validation performs no shape/BE callbacks. */
    private static final class HostObservations {
        private final ServerLevel host;
        private final Map<ChunkKey, LevelChunk> chunks = new HashMap<>();
        private final Map<BlockPos, BlockState> states = new HashMap<>();
        private final Map<BlockPos, @Nullable BlockEntity> blockEntities = new HashMap<>();
        private int reads;

        private HostObservations(ServerLevel host) {
            this.host = Objects.requireNonNull(host, "host");
        }

        private LevelChunk chunk(BlockPos position) {
            if (++reads > MAX_HOST_READS) throw new UnavailableRay();
            var key = new ChunkKey(position.getX() >> 4, position.getZ() >> 4);
            var chunk = host.getChunkSource().getChunkNow(key.x, key.z);
            if (chunk == null) throw new UnavailableRay();
            var previous = chunks.putIfAbsent(key, chunk);
            if (previous != null && previous != chunk) throw new UnavailableRay();
            return chunk;
        }

        private BlockState state(BlockPos position) {
            var state = chunk(position).getBlockState(position);
            var previous = states.putIfAbsent(position.immutable(), state);
            if (previous != null && previous != state) throw new UnavailableRay();
            return state;
        }

        private @Nullable BlockEntity blockEntity(BlockPos position) {
            state(position);
            var entity = chunk(position).getBlockEntity(position);
            if (blockEntities.containsKey(position) && blockEntities.get(position) != entity) throw new UnavailableRay();
            blockEntities.put(position.immutable(), entity);
            return entity;
        }

        private boolean stillValid() {
            if (host.getServer().getLevel(host.dimension()) != host) return false;
            for (var entry : chunks.entrySet()) {
                var key = entry.getKey();
                if (host.getChunkSource().getChunkNow(key.x, key.z) != entry.getValue()) return false;
            }
            for (var entry : states.entrySet()) {
                var position = entry.getKey();
                var chunk = chunks.get(new ChunkKey(position.getX() >> 4, position.getZ() >> 4));
                if (chunk == null || chunk.getBlockState(position) != entry.getValue()) return false;
            }
            for (var entry : blockEntities.entrySet()) {
                var position = entry.getKey();
                var chunk = chunks.get(new ChunkKey(position.getX() >> 4, position.getZ() >> 4));
                if (chunk == null || chunk.getBlockEntities().get(position) != entry.getValue()) return false;
            }
            return true;
        }
    }

    private record ChunkKey(int x, int z) {}
}
