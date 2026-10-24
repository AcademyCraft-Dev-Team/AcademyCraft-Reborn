package org.academy.internal.common.structure.sable;

import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.collider.SableCollisionContext;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.physics.chunk.VoxelNeighborhoodState;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructureDirectControl;
import org.academy.api.common.structure.StructureRef;
import org.academy.internal.common.structure.upgrade.control.ExtractionPlanner;
import org.academy.internal.common.structure.upgrade.control.OrientationBackendAccess;
import org.academy.internal.common.structure.upgrade.control.OrientationController;
import org.academy.internal.common.structure.upgrade.control.OrientationTargets;
import org.joml.Matrix3dc;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Optional linkage boundary for angular control of loaded, unit-scale Sable block bodies. */
public final class SableOrientationAccess implements OrientationBackendAccess {
    public static final SableOrientationAccess INSTANCE = new SableOrientationAccess();
    private static final String RAPIER_PIPELINE = "dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline";
    private static final int MAX_SCANNED_BODIES = 4_096;
    private static final int MAX_GEOMETRY_CELLS = 65_536;
    private static final int MAX_CACHED_BODIES = 256;
    private static final double MAX_POSITION_ROUNDING = 1E-4;
    private static final OrientationTargets.Baselines NO_BASELINE =
            new OrientationTargets.Baselines(Optional.empty(), Optional.empty());
    private final Map<SableStructureHandle, Geometry> geometry = new IdentityHashMap<>();

    private SableOrientationAccess() {}

    @Override public Vec3 positionRounding(Body body) {
        var center = body.motion().pose().localToWorldPoint(body.motion().localCenterOfMass());
        return new Vec3(positionRounding(center.x), positionRounding(center.y), positionRounding(center.z));
    }

    /**
     * The pinned Rapier bridge publishes a float world COM, then subtracts the double local COM.
     * Half a float ULP bounds that position observation, independently on each world axis.
     * Never widen a contact beyond 0.0001 blocks: a coordinate whose half-ULP exceeds that cap
     * gets no exemption. Thus ordinary coordinates below |2048| qualify, while coarse distant
     * observations fail closed instead of turning centimetres of penetration into rounding.
     */
    static double positionRounding(double coordinate) {
        if (!Double.isFinite(coordinate)) return 0;
        double halfUlp = Math.ulp((float) coordinate) * .5;
        return Double.isFinite(halfUlp) && halfUlp <= MAX_POSITION_ROUNDING ? halfUlp : 0;
    }

    @Override public Optional<Body> observe(ServerLevel host, StructureRef reference) {
        SableStructureAccess.requireThread(host.getServer());
        var handle = SableStructureAccess.resolveHandle(host.getServer(), reference).orElse(null);
        // Read-only snapshots also describe static/paused/foreign-owned obstacles. Admission is a lease decision.
        return handle == null || handle.host() != host ? Optional.empty() : observation(handle);
    }

    @Override public Nearby nearby(ServerLevel host, AABB bounds, int maximumBodies) {
        SableStructureAccess.requireThread(host.getServer());
        if (maximumBodies < 0) throw new IllegalArgumentException("Negative candidate budget");
        var container = SubLevelContainer.getContainer(host);
        if (container == null) return new Nearby(List.of(), true);
        var result = new ArrayList<Body>();
        int scanned = 0;
        for (var subLevel : container.getAllSubLevels()) {
            if (++scanned > MAX_SCANNED_BODIES) return new Nearby(result, false);
            if (subLevel.isRemoved() || subLevel.getLevel() != host) continue;
            var plotBounds = subLevel.getPlot().getBoundingBox();
            if (plotBounds == null) continue;
            var box = new AABB(plotBounds.minX(), plotBounds.minY(), plotBounds.minZ(),
                    plotBounds.maxX() + 1.0, plotBounds.maxY() + 1.0, plotBounds.maxZ() + 1.0);
            var reference = new StructureRef(subLevel.getUniqueId(), host.dimension().identifier(),
                    SableStructureAccess.ID, 0);
            var handle = SableStructureAccess.resolveHandle(host.getServer(), reference).orElse(null);
            if (handle == null) return new Nearby(result, false);
            try {
                var system = SubLevelPhysicsSystem.get(host);
                boolean nativeBody = readable(system, handle);
                var linear = nativeBody ? vector(system.getPhysicsHandle(subLevel).getLinearVelocity(new Vector3d()))
                        : Vec3.ZERO;
                var mass = subLevel.getMassTracker();
                var plotCenter = mass != null && mass.getCenterOfMass() != null
                        ? vector(mass.getCenterOfMass()) : box.getCenter();
                var center = subLevel.logicalPose().transformPosition(plotCenter);
                var gravity = nativeBody && !system.getPaused()
                        ? vector(DimensionPhysicsData.getGravity(host, new Vector3d(center.x, center.y, center.z))) : Vec3.ZERO;
                var extended = contraptionBounds(handle, box, system == null ? 1.0 : system.getPartialPhysicsTick());
                var worldBounds = SableStructureTransforms.worldBounds(subLevel.logicalPose(), extended);
                if (!motionBounds(worldBounds, center, linear.add(gravity.scale(0.05)))
                        .inflate(0.5 * gravity.length() * 0.05 * 0.05).intersects(bounds)) continue;
                // Only a potentially intersecting assembly with unsupported kinematic geometry denies this query.
                if (!subLevel.getPlot().getContraptions().isEmpty()) return new Nearby(result, false);
                if (result.size() >= maximumBodies) return new Nearby(result, false);
                var body = observation(handle).orElse(null);
                if (body == null) return new Nearby(result, false);
                result.add(body);
            } catch (IllegalStateException | IllegalArgumentException unavailable) {
                return new Nearby(result, false);
            }
        }
        return new Nearby(result, true);
    }

    @Override public boolean apply(ServerLevel host, Body expected, Vec3 angularVelocity) {
        SableStructureAccess.requireThread(host.getServer());
        finite(angularVelocity, "angularVelocity");
        if (SableStructureDirectControl.probe(host, expected.reference()).state()
                != StructureDirectControl.Probe.State.READY) return false;
        var current = observe(host, expected.reference()).orElse(null);
        if (!expected.sameObservation(current) || !current.terrainReady()) return false;
        var handle = SableStructureAccess.resolveHandle(host.getServer(), expected.reference()).orElse(null);
        var system = SubLevelPhysicsSystem.get(host);
        if (handle == null || !readable(system, handle)) return false;
        var correction = angularCorrection(current.motion().angularVelocity(), angularVelocity);
        // This API takes world velocities. Zero linear correction preserves the actual COM motion.
        system.getPhysicsHandle(handle.subLevel()).addLinearAndAngularVelocity(new Vector3d(),
                new Vector3d(correction.x, correction.y, correction.z));
        return true;
    }

    public static void shutdown(MinecraftServer server) {
        SableStructureAccess.requireThread(server);
        INSTANCE.geometry.keySet().removeIf(handle -> handle.host().getServer() == server);
    }

    @Override public Optional<LinearBody> observeLinear(ServerLevel host, StructureRef reference) {
        var body = observe(host, reference);
        var handle = SableStructureAccess.resolveHandle(host.getServer(), reference).orElse(null);
        if (body.isEmpty() || handle == null || handle.subLevel().getMassTracker() == null) return Optional.empty();
        double mass = handle.subLevel().getMassTracker().getMass();
        return Double.isFinite(mass) && mass > 0 ? Optional.of(new LinearBody(body.get(), mass)) : Optional.empty();
    }

    @Override public void cancelQueuedTranslation(ServerLevel host, StructureRef reference, java.util.UUID controlId) {
        SableStructureDirectControl.cancel(host, reference, controlId);
    }

    @Override public boolean applyLinear(ServerLevel host, LinearBody expected, Vec3 linearVelocity) {
        return applyMotion(host, expected, linearVelocity, expected.body().motion().angularVelocity());
    }

    @Override public boolean applyMotion(ServerLevel host, LinearBody expected, Vec3 linearVelocity, Vec3 angularVelocity) {
        SableStructureAccess.requireThread(host.getServer());
        finite(linearVelocity, "linearVelocity");
        finite(angularVelocity, "angularVelocity");
        if (SableStructureDirectControl.probe(host, expected.body().reference()).state()
                != StructureDirectControl.Probe.State.READY) return false;
        var current = observeLinear(host, expected.body().reference()).orElse(null);
        if (current == null || current.mass() != expected.mass() || !expected.body().terrainReady()
                || !expected.body().sameObservation(current.body())) return false;
        var handle = SableStructureAccess.resolveHandle(host.getServer(), expected.body().reference()).orElse(null);
        var system = SubLevelPhysicsSystem.get(host);
        if (handle == null || !readable(system, handle)) return false;
        var correction = linearVelocity.subtract(current.body().linearVelocity());
        var angular = angularCorrection(current.body().motion().angularVelocity(), angularVelocity);
        system.getPhysicsHandle(handle.subLevel()).addLinearAndAngularVelocity(
                new Vector3d(correction.x, correction.y, correction.z), new Vector3d(angular.x, angular.y, angular.z));
        return true;
    }

    private Optional<Body> observation(SableStructureHandle handle) {
        try {
            if (!handle.isCurrent() || !SableStructureTransforms.unitScale(handle.subLevel().logicalPose())
                    || !handle.subLevel().getPlot().getContraptions().isEmpty()) return Optional.empty();
            var host = handle.host();
            var system = SubLevelPhysicsSystem.get(host);
            boolean nativeBody = readable(system, handle);
            boolean ready = nativeBody && !system.getPaused() && SableStructureTraction.controllable(handle);
            var shape = geometry(handle);
            if (shape.boxes().isEmpty()) return Optional.empty();
            var mass = handle.subLevel().getMassTracker();
            var center = mass != null && mass.getCenterOfMass() != null
                    ? vector(mass.getCenterOfMass()).subtract(Vec3.atLowerCornerOf(handle.plotOrigin())) : Vec3.ZERO;
            double inertia = mass != null && mass.getMass() > 0 ? inertiaBound(mass.getInertiaTensor()) : 1.0;
            var rigid = nativeBody ? system.getPhysicsHandle(handle.subLevel()) : null;
            var angular = rigid == null ? Vec3.ZERO : vector(rigid.getAngularVelocity(new Vector3d()));
            var linear = rigid == null ? Vec3.ZERO : vector(rigid.getLinearVelocity(new Vector3d()));
            // Sable publishes logicalPose after EVERY solver substep, before the next pre-physics event.
            var pose = handle.pose();
            var worldCenter = pose.localToWorldPoint(center);
            var gravity = nativeBody && !system.getPaused() ? vector(DimensionPhysicsData.getGravity(host,
                    new Vector3d(worldCenter.x, worldCenter.y, worldCenter.z))) : Vec3.ZERO;
            return Optional.of(new Body(handle.reference(), handle.runtimeId(), handle.contentRevision(),
                    handle.poseRevision(), new OrientationController.State(pose, center, angular, inertia),
                    linear, gravity, shape.boxes(), NO_BASELINE, ready));
        } catch (IllegalStateException | IllegalArgumentException unavailable) {
            return Optional.empty();
        }
    }

    private Geometry geometry(SableStructureHandle handle) {
        var plot = handle.subLevel().getPlot();
        var bounds = plot.getBoundingBox();
        if (bounds == null) return new Geometry(handle.contentRevision(), null, List.of());
        var keyBounds = new AABB(bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX() + 1.0, bounds.maxY() + 1.0, bounds.maxZ() + 1.0);
        var cached = geometry.get(handle);
        if (cached != null && cached.revision() == handle.contentRevision() && keyBounds.equals(cached.bounds())) {
            return cached;
        }
        double cells = keyBounds.getXsize() * keyBounds.getYsize() * keyBounds.getZsize();
        if (!Double.isFinite(cells) || cells > MAX_GEOMETRY_CELLS) {
            throw new IllegalStateException("Sable collision snapshot exceeds its cell budget");
        }
        var origin = handle.plotOrigin();
        var shapes = new IdentityHashMap<BlockState, List<AABB>>();
        var result = new ArrayList<AABB>();
        for (var position : BlockPos.betweenClosed(bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ())) {
            var holder = plot.getChunkHolder(plot.toLocal(new ChunkPos(position.getX() >> 4, position.getZ() >> 4)));
            if (holder == null) throw new IllegalStateException("Sable collision snapshot has an unloaded plot chunk");
            var state = holder.getChunk().getBlockState(position);
            if (state.isAir()) continue;
            var boxes = shapes.computeIfAbsent(state, value -> collisionBoxes(value, handle.host()));
            for (var box : boxes) {
                if (result.size() >= ExtractionPlanner.MAX_LOCAL_BOXES) {
                    throw new IllegalStateException("Sable collision snapshot exceeds its shape budget");
                }
                result.add(box.move(position.getX() - (double) origin.getX(), position.getY() - (double) origin.getY(),
                        position.getZ() - (double) origin.getZ()));
            }
        }
        var snapshot = new Geometry(handle.contentRevision(), keyBounds, List.copyOf(result));
        if (geometry.size() >= MAX_CACHED_BODIES) {
            geometry.keySet().removeIf(candidate -> !candidate.isCurrent());
            if (geometry.size() >= MAX_CACHED_BODIES) geometry.clear();
        }
        geometry.put(handle, snapshot);
        return snapshot;
    }

    private static AABB contraptionBounds(SableStructureHandle handle, AABB blockBounds, double partialTick) {
        var result = blockBounds;
        var pivot = new Vec3(0.5, 0.5, 0.5);
        for (var contraption : handle.subLevel().getPlot().getContraptions()) {
            if (!contraption.sable$isValid() || !contraption.sable$shouldCollide()) continue;
            var bounds = new BoundingBox3i(0, 0, 0, 0, 0, 0);
            contraption.sable$getLocalBounds(bounds);
            var local = new AABB(bounds.minX(), bounds.minY(), bounds.minZ(),
                    bounds.maxX() + 1.0, bounds.maxY() + 1.0, bounds.maxZ() + 1.0);
            // The pinned Create adapter rotates about cell center and linearly interpolates its anchor.
            // Transforming that pivot removes rotation, so both ends plus a full radius cover the arc.
            var from = contraption.sable$getLocalPose(new Pose3d(), partialTick - 1.0).transformPosition(pivot);
            var to = contraption.sable$getLocalPose(new Pose3d(), partialTick + 1.0).transformPosition(pivot);
            result = result.minmax(pivotMotionBounds(local, pivot, from, to));
        }
        return result;
    }

    static AABB pivotMotionBounds(AABB local, Vec3 pivot, Vec3 from, Vec3 to) {
        finite(pivot, "contraption pivot");
        finite(from, "contraption anchor");
        finite(to, "contraption anchor");
        double x = Math.max(Math.abs(local.minX - pivot.x), Math.abs(local.maxX - pivot.x));
        double y = Math.max(Math.abs(local.minY - pivot.y), Math.abs(local.maxY - pivot.y));
        double z = Math.max(Math.abs(local.minZ - pivot.z), Math.abs(local.maxZ - pivot.z));
        double radius = Math.hypot(Math.hypot(x, y), z);
        if (!Double.isFinite(radius)) throw new IllegalStateException("Invalid contraption bounds");
        return new AABB(from, to).inflate(radius);
    }

    /** Match the pinned Sable collider's public custom-shape/context contract and cell clipping. */
    private static List<AABB> collisionBoxes(BlockState state, ServerLevel host) {
        // Sable fluid colliders supply buoyancy; they are not solid rotational obstructions.
        if (VoxelNeighborhoodState.isLiquid(state)) return List.of();
        var getter = new ColliderView(state, host.getMinY(), host.getHeight());
        var shape = state.getBlock() instanceof BlockSubLevelCollisionShape custom
                ? custom.getSubLevelCollisionShape(getter, state)
                : state.getCollisionShape(getter, BlockPos.ZERO, SableCollisionContext.get());
        return clipToCell(shape.toAabbs());
    }

    static List<AABB> clipToCell(List<AABB> boxes) {
        var result = new ArrayList<AABB>();
        for (var box : boxes) {
            double minX = Math.max(0, box.minX), minY = Math.max(0, box.minY), minZ = Math.max(0, box.minZ);
            double maxX = Math.min(1, box.maxX), maxY = Math.min(1, box.maxY), maxZ = Math.min(1, box.maxZ);
            if (minX < maxX && minY < maxY && minZ < maxZ) result.add(new AABB(minX, minY, minZ, maxX, maxY, maxZ));
        }
        return List.copyOf(result);
    }

    /** Gershgorin bound includes off-diagonal and merged parallel-axis inertia, unlike max(diagonal). */
    static double inertiaBound(Matrix3dc inertia) {
        double bound = 0;
        for (int row = 0; row < 3; row++) {
            double sum = 0;
            for (int column = 0; column < 3; column++) sum += Math.abs(inertia.get(column, row));
            bound = Math.max(bound, sum);
        }
        if (!Double.isFinite(bound) || bound <= 0) throw new IllegalArgumentException("Invalid Sable inertia tensor");
        return bound;
    }

    /** A rotating body can visit its full COM sphere; the translation endpoint alone is insufficient. */
    static AABB motionBounds(AABB worldBounds, Vec3 center, Vec3 linearVelocity) {
        finite(center, "center");
        finite(linearVelocity, "linearVelocity");
        double x = Math.max(Math.abs(worldBounds.minX - center.x), Math.abs(worldBounds.maxX - center.x));
        double y = Math.max(Math.abs(worldBounds.minY - center.y), Math.abs(worldBounds.maxY - center.y));
        double z = Math.max(Math.abs(worldBounds.minZ - center.z), Math.abs(worldBounds.maxZ - center.z));
        double radius = Math.hypot(Math.hypot(x, y), z);
        if (!Double.isFinite(radius)) throw new IllegalArgumentException("Invalid Sable bounds");
        return new AABB(center, center).inflate(radius).expandTowards(linearVelocity.scale(0.05));
    }

    static Vec3 angularCorrection(Vec3 observed, Vec3 requested) {
        finite(observed, "observed angular velocity");
        finite(requested, "requested angular velocity");
        return finite(requested.subtract(observed), "angular correction");
    }

    private static boolean readable(@Nullable SubLevelPhysicsSystem system, SableStructureHandle handle) {
        return system != null && RAPIER_PIPELINE.equals(system.getPipeline().getClass().getName())
                && handle.subLevel().getRuntimeId() != PhysicsPipelineBody.NULL_RUNTIME_ID;
    }

    private static Vec3 vector(org.joml.Vector3dc value) { return new Vec3(value.x(), value.y(), value.z()); }

    private static Vec3 finite(Vec3 value, String name) {
        if (value == null || !Double.isFinite(value.x) || !Double.isFinite(value.y) || !Double.isFinite(value.z)) {
            throw new IllegalArgumentException("Non-finite " + name);
        }
        return value;
    }

    private record Geometry(long revision, @Nullable AABB bounds, List<AABB> boxes) {}

    /** Physics shapes are keyed by blockstate in Sable and see no mutable neighbor/BE state. */
    private record ColliderView(BlockState state, int minY, int height) implements BlockGetter {
        @Override public @Nullable BlockEntity getBlockEntity(BlockPos position) { return null; }
        @Override public BlockState getBlockState(BlockPos position) {
            return position.equals(BlockPos.ZERO) ? state : Blocks.AIR.defaultBlockState();
        }
        @Override public FluidState getFluidState(BlockPos position) { return getBlockState(position).getFluidState(); }
        @Override public int getMinY() { return minY; }
        @Override public int getHeight() { return height; }
    }
}
