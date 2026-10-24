package org.academy.api.common.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * Draft v2 ports for bounded structure work. Not an installed gameplay implementation.
 * Existing capture journals, control leases and permission checks remain authoritative.
 * World mutation/publication belongs to the server thread; geometry inputs are detached snapshots.
 */
public interface StructurePipeline {
    /** Resume the existing transaction, stopping at a durable boundary within the slice budget. */
    CaptureSlice advanceCapture(ServerLevel host, UUID transactionId, WorkLimit limit);

    /** Compile exact occupied volume without pairwise restart merging or hidden interior faces. */
    Geometry compileGeometry(GeometryInput input, WorkLimit limit);

    /** Publish only matching epochs/revisions; required coverage is atomic before a physics step. */
    TerrainReceipt publishTerrain(ServerLevel host, TerrainPublication publication);

    /** Compute force/torque at the actual solver substep, in seconds; do not integrate an outer tick twice. */
    SubstepForce controlSubstep(StructurePhysicsControl command, StructureMotionState motion, double seconds);

    /** Latch arrival once; start the 200 tick hover clock at arrival, separately from lift timeout. */
    LiftState advanceLift(LiftRequest request, LiftState previous, StructureDynamics.State actual, long serverTick);

    /** Bounded history, ordered by epoch/sequence; render and contact use the same sampled pose. */
    PoseSample samplePose(List<PoseFrame> history, double serverTick, double maximumExtrapolationTicks);

    /** Select reusable section meshes by content/light/resource revisions, including 9..128 cell structures. */
    MeshPlan planMesh(MeshRequest request);

    record WorkLimit(long maximumNanos, int maximumCells, int maximumPublications) {}
    enum CaptureStage { PREPARE, STAGE, DURABLE, COMMIT, PUBLISH, COMPLETE, FAILED }
    record CaptureSlice(CaptureStage stage, int processedCells, boolean durable, String detail) {}
    record Epoch(StructureRef reference, UUID runtimeId, long revision) {}
    /** Boxes are cell-relative. Dynamic localCell is structure-local; terrain localCell is section-local. */
    record CellShape(BlockPos localCell, List<AABB> boxes, int material) {
        public CellShape { localCell = localCell.immutable(); boxes = List.copyOf(boxes); }
    }
    record GeometryInput(Epoch epoch, List<CellShape> cells, boolean terrain) {
        public GeometryInput { cells = List.copyOf(cells); }
    }
    /** Bounds use the input's structure/section-local frame; material IDs belong to its snapshot. */
    record ShapePart(AABB bounds, int material) {}
    record Geometry(Epoch epoch, List<ShapePart> parts, int visitedCells, boolean complete) {
        public Geometry { parts = List.copyOf(parts); }
    }
    record TerrainSection(long sectionKey, long sourceRevision, Geometry geometry) {}
    record TerrainPublication(Epoch epoch, long coverageGeneration, List<TerrainSection> sections,
                              List<Long> requiredSections) {
        public TerrainPublication { sections = List.copyOf(sections); requiredSections = List.copyOf(requiredSections); }
    }
    enum PublicationStatus { APPLIED, STALE, INCOMPLETE, UNAVAILABLE }
    record TerrainReceipt(PublicationStatus status, long appliedGeneration, String detail) {}
    record SubstepForce(Vec3 force, Vec3 torque, Vec3 worldPoint) {}
    enum LiftPhase { LIFTING, HOVERING, LAUNCHED, BLOCKED, EXPIRED, CANCELLED }
    record LiftRequest(Epoch epoch, Vec3 sourceCenter, double rise, int hoverTicks, long liftDeadlineTick) {}
    /** arrivalTick/hoverDeadlineTick are -1 until the first valid arrival. */
    record LiftState(Epoch epoch, LiftPhase phase, long arrivalTick, long hoverDeadlineTick, String detail) {}
    record PoseFrame(Epoch epoch, long sequence, long serverTick, StructurePose pose,
                     Vec3 linearVelocity, Vec3 angularVelocity, boolean teleport) {}
    record PoseSample(Epoch epoch, StructurePose pose, long sequence, boolean extrapolated, boolean stale) {}
    record MeshRequest(Epoch epoch, long lightingRevision, long resourceEpoch, List<Long> dirtySections,
                       int cellCount, boolean containsBlockEntities) {
        public MeshRequest { dirtySections = List.copyOf(dirtySections); }
    }
    record MeshPlan(List<Long> rebuildSections, boolean reuseGeometry, boolean renderBlockEntitiesSeparately) {
        public MeshPlan { rebuildSections = List.copyOf(rebuildSections); }
    }
}
