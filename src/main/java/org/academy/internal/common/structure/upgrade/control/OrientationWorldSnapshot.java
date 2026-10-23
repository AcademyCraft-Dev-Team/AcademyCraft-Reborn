package org.academy.internal.common.structure.upgrade.control;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.academy.internal.common.structure.StructureBackendRouter;
import org.academy.internal.common.structure.BlockStructureCollisionRuntime;
import org.academy.internal.common.structure.BlockStructureSettlementMotion;
import org.academy.internal.common.structure.NativeStructureRuntime;
import org.academy.internal.common.world.entity.structure.BlockStructureEntity;
import org.academy.internal.common.world.entity.structure.NativeStructureProjectionEntity;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One substep's bounded, lazy host-shape snapshot; never obtains chunk tickets. */
final class OrientationWorldSnapshot implements ExtractionPlanner.ObstacleSnapshot {
    private static final int MAX_CELLS = 16_384;
    private static final int MAX_NEARBY = 128;
    private final ServerLevel host;
    private final long revision;
    private final int[] sharedRemainingCells;
    private final Map<BlockPos, Cell> cells = new HashMap<>();
    private final List<OtherBody> bodies = new ArrayList<>();
    private final List<ExtractionPlanner.Obstacle> movingObstacles = new ArrayList<>();
    private final BlockGetter loadedView = new LoadedView();
    private final AABB sweptBodyBounds;
    private boolean complete = true;

    OrientationWorldSnapshot(ServerLevel host, OrientationBackendAccess.Body controlled, long revision,
                             double seconds, int[] sharedRemainingCells) {
        this.host = host;
        this.revision = revision;
        this.sharedRemainingCells = sharedRemainingCells;
        var center = controlled.motion().pose().localToWorldPoint(controlled.motion().localCenterOfMass());
        double radius = radius(controlled.localBoxes(), controlled.motion().localCenterOfMass());
        var bounds = AABB.ofSize(center, radius * 2, radius * 2, radius * 2)
                .expandTowards(controlled.freeStepDisplacement(seconds)).inflate(controlled.freeStepMargin(seconds) + 1.0E-6);
        sweptBodyBounds = bounds;
        if (!legacyClear(bounds)) { complete = false; return; }
        for (var backend : StructureBackendRouter.orientationBackends(host)) {
            var nearby = backend.nearby(host, bounds, MAX_NEARBY - bodies.size());
            complete &= nearby.complete();
            for (var body : nearby.bodies()) {
                if (body.reference().equals(controlled.reference())) continue;
                if (bodies.size() >= MAX_NEARBY) { complete = false; break; }
                bodies.add(new OtherBody(backend, body));
                var pose = body.motion().pose();
                var middle = OrientationTargets.integrateRotation(pose, body.motion().localCenterOfMass(),
                        body.motion().angularVelocity(), seconds / 2);
                var displacement = body.freeStepDisplacement(seconds);
                middle = new StructurePose(middle.worldOrigin().add(displacement.scale(0.5)), middle.rotation());
                double halfAngle = body.motion().angularVelocity().length() * seconds / 2;
                double movement = displacement.length() / 2 + body.freeStepMargin(seconds);
                for (var local : body.localBoxes()) {
                    if (movingObstacles.size() >= ExtractionPlanner.MAX_OBSTACLE_SHAPES) { complete = false; break; }
                    double r = radius(List.of(local), body.motion().localCenterOfMass());
                    double margin = 2 * r * Math.sin(Math.min(Math.PI, halfAngle) / 2) + movement;
                    movingObstacles.add(new ExtractionPlanner.Obstacle(body.reference().id().toString(),
                            ExtractionPlanner.ObstacleKind.HARD, TranslationSweep.Box.of(local.inflate(margin), middle)));
                }
            }
        }
    }

    @Override
    public ExtractionPlanner.Query query(AABB bounds, int maximumShapes) {
        if (!complete) return unknown();
        var result = new ArrayList<ExtractionPlanner.Obstacle>();
        // Neighbour cells are required for fences/walls whose shapes extend beyond their cell.
        int minX = (int) Math.floor(bounds.minX) - 1, minY = (int) Math.floor(bounds.minY) - 1;
        int minZ = (int) Math.floor(bounds.minZ) - 1, maxX = (int) Math.floor(bounds.maxX) + 1;
        int maxY = (int) Math.floor(bounds.maxY) + 1, maxZ = (int) Math.floor(bounds.maxZ) + 1;
        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (volume <= 0 || volume > MAX_CELLS) return truncated(result);
        try {
            for (var mutable : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
                var cell = read(mutable);
                if (cell.shapes == null) {
                    var position = mutable.immutable();
                    // Neighbor and block-entity reads also pass through the bounded loaded-only view.
                    cell.shapes = cell.state.getCollisionShape(loadedView, position).toAabbs().stream()
                            .map(box -> TranslationSweep.Box.axisAligned(box.move(position))).toList();
                }
                for (var shape : cell.shapes) {
                    if (!shape.bounds().intersects(bounds)) continue;
                    if (result.size() >= maximumShapes) return truncated(result);
                    result.add(new ExtractionPlanner.Obstacle("host:" + mutable.asLong(), ExtractionPlanner.ObstacleKind.HARD, shape));
                }
            }
        } catch (Unavailable unavailable) {
            return unavailable.budget ? truncated(result) : unknown();
        }
        for (var shape : movingObstacles) {
            if (!shape.box().bounds().intersects(bounds)) continue;
            if (result.size() >= maximumShapes) return truncated(result);
            result.add(shape);
        }
        return new ExtractionPlanner.Query(revision, ExtractionPlanner.Coverage.COMPLETE, false, result);
    }

    /** Recheck the same immutable states immediately before the native correction. */
    boolean current() {
        if (!complete || !legacyClear(sweptBodyBounds)) return false;
        for (var cell : cells.entrySet()) {
            var position = cell.getKey();
            var observed = cell.getValue();
            var chunk = host.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
            if (chunk != observed.chunk || chunk.getBlockState(position) != observed.state
                    || chunk.getBlockEntities().get(position) != observed.entity) return false;
        }
        for (var other : bodies) {
            if (!other.body.sameObservation(other.backend.observe(host, other.body.reference()).orElse(null))) return false;
        }
        return true;
    }

    /** Older entities/regions have no shared angular adapter; intersecting coverage stays unknown. */
    private boolean legacyClear(AABB bounds) {
        var runtime = NativeStructureRuntime.existing(host.getServer()).orElse(null);
        if (runtime != null) {
            for (var reference : runtime.loadedReferences()) {
                if (!reference.dimension().equals(host.dimension().identifier())) continue;
                var region = runtime.attachedPhysicsRegion(host, reference).orElse(null);
                // The old native engine guarantees containment in this fixed region, including its next step.
                if (region != null && region.bounds().intersects(bounds)) return false;
            }
        }
        // The entity backend caps movement at 16 blocks per outer tick. Its broad-phase index
        // stores the current shape, so include incoming entities before checking actual motion.
        var candidates = bounds.inflate(16.1);
        if (!boundedLegacyQuery(candidates)) return false;
        for (var entity : BlockStructureCollisionRuntime.query(host, candidates)) {
            if (entity instanceof BlockStructureEntity structure) {
                var motion = structure.getDeltaMovement();
                var envelope = legacyEntityEnvelope(structure.getBoundingBox(), motion, structure.gravityEnabled());
                if (envelope.intersects(bounds)) return false;
            } else if (entity instanceof NativeStructureProjectionEntity projection
                    && projection.getBoundingBox().intersects(bounds) && projection.hasCollision()) {
                return false;
            }
        }
        return true;
    }

    static AABB legacyEntityEnvelope(AABB bounds, Vec3 movementPerTick, boolean gravity) {
        // Include a complete vanilla entity tick irrespective of this solver's smaller substep.
        return bounds.expandTowards(movementPerTick)
                .expandTowards(0, gravity ? -BlockStructureSettlementMotion.FALLING_BLOCK_GRAVITY : 0, 0);
    }

    static boolean boundedLegacyQuery(AABB bounds) {
        double x = Math.floor(bounds.maxX / 16) - Math.floor(bounds.minX / 16) + 1;
        double y = Math.floor(bounds.maxY / 16) - Math.floor(bounds.minY / 16) + 1;
        double z = Math.floor(bounds.maxZ / 16) - Math.floor(bounds.minZ / 16) + 1;
        double sections = x * y * z;
        return Double.isFinite(sections) && sections > 0 && sections <= 4_096;
    }

    static double radius(List<AABB> boxes, Vec3 center) {
        double radius = 0;
        for (var box : boxes) {
            double x = Math.max(Math.abs(box.minX - center.x), Math.abs(box.maxX - center.x));
            double y = Math.max(Math.abs(box.minY - center.y), Math.abs(box.maxY - center.y));
            double z = Math.max(Math.abs(box.minZ - center.z), Math.abs(box.maxZ - center.z));
            radius = Math.max(radius, Math.hypot(Math.hypot(x, y), z));
        }
        return radius;
    }

    private ExtractionPlanner.Query unknown() {
        return new ExtractionPlanner.Query(revision, ExtractionPlanner.Coverage.UNKNOWN, false, List.of());
    }

    private ExtractionPlanner.Query truncated(List<ExtractionPlanner.Obstacle> collected) {
        return new ExtractionPlanner.Query(revision, ExtractionPlanner.Coverage.COMPLETE, true, collected);
    }

    private Cell read(BlockPos position) {
        var known = cells.get(position);
        if (known != null) return known;
        if (!host.isInWorldBounds(position) || !host.getWorldBorder().isWithinBounds(position)) {
            throw new Unavailable(false);
        }
        var chunk = host.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
        if (chunk == null) throw new Unavailable(false);
        if (cells.size() >= MAX_CELLS || sharedRemainingCells[0] <= 0) throw new Unavailable(true);
        sharedRemainingCells[0]--;
        var cell = new Cell(chunk, chunk.getBlockState(position), chunk.getBlockEntities().get(position));
        cells.put(position.immutable(), cell);
        return cell;
    }

    private final class LoadedView implements BlockGetter {
        @Override public BlockState getBlockState(BlockPos position) { return read(position).state; }
        @Override public FluidState getFluidState(BlockPos position) { return read(position).state.getFluidState(); }
        @Override public @Nullable BlockEntity getBlockEntity(BlockPos position) { return read(position).entity; }
        @Override public int getHeight() { return host.getHeight(); }
        @Override public int getMinY() { return host.getMinY(); }
    }

    private static final class Cell {
        final LevelChunk chunk;
        final BlockState state;
        final @Nullable BlockEntity entity;
        @Nullable List<TranslationSweep.Box> shapes;

        Cell(LevelChunk chunk, BlockState state, @Nullable BlockEntity entity) {
            this.chunk = chunk; this.state = state; this.entity = entity;
        }
    }

    private static final class Unavailable extends RuntimeException {
        final boolean budget;
        Unavailable(boolean budget) { super(null, null, false, false); this.budget = budget; }
    }

    private record OtherBody(OrientationBackendAccess backend, OrientationBackendAccess.Body body) {}
}
