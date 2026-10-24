package org.academy.api.common.structure.upgrade;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructureCaptureRequest;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Planned collision-aware extraction over the same exclusive lease as direct structure skills.
 * Calls neither acquire authority nor load chunks. Every operation must revalidate the actor,
 * exact activation, content/pose/terrain versions and the supplied control ID on the server thread.
 * A plan is an observation, not a transferable capability or permission to teleport a body.
 */
public interface StructureExtraction {
    int MAX_DURATION_TICKS = 1_200;
    int MAX_PATH_POINTS = 512;
    int MAX_BLOCKED_CELLS = 256;
    int MAX_SHAPE_TESTS = 262_144;
    double MAX_LIFT_HEIGHT = 64.0;
    double MAX_RETREAT_DISTANCE = 2.0;

    /**
     * Inspect host source cells before a structure/runtime exists. The service records the actor,
     * source snapshot digest, terrain revision and expiry server-side. The capture predicate is
     * an additional restriction, never a substitute for actor permissions. This does not capture,
     * acquire a control lease, load chunks or authorize source writes.
     * The current live preview supports state-only cells; block entities, fluids and pending
     * block ticks are explicitly refused. Submit the same {@link StructureCaptureRequest}
     * instance to the capture coordinator so its completion can be joined to this preview.
     */
    StructureOperation.Result<CapturePlan> planCapture(StructureOperation.ActorContext context,
                                                       CaptureRequest request);

    /**
     * Resolve planId and captureOperationId from server-owned registries/journal. Require the
     * capture's completed result, source snapshot digest, host, actor and exact activation to
     * match the saved preview and this context; then validate the control lease. PENDING capture
     * is not success. Revalidate terrain/expiry and allow source initialization only before the
     * first physics step. A caller-supplied CapturePlan or StructureCaptureResult is never proof.
     * Success returns a bound Plan whose ID can be passed to begin; binding does not start motion.
     * One preview binds to one capture/control pair; an identical retry returns the same binding,
     * and reuse with another capture, actor or control returns CONFLICT or DENIED.
     * The live coordinator retains the submitted request identity, source cells and completed
     * activation/content observation. Missing or recovered evidence is not bindable. The legacy
     * capture entry point has no actor parameter; actor authority comes from the saved preview,
     * mandatory interaction restrictions and the current exact lease, not the result alone.
     */
    StructureOperation.Result<Plan> bindCapture(StructureOperation.Context context, BindCaptureRequest request);

    /**
     * Plan extraction of a resolved structure without moving or removing content. NEW_CAPTURE
     * observations require an already verified server-side capture binding; the enum alone
     * cannot grant source-pose initialization rights.
     */
    StructureOperation.Result<Plan> plan(StructureOperation.Context context, PlanRequest request);

    /** Resolve a server-owned plan ID and revalidate it before starting bounded extraction. */
    StructureOperation.Result<Snapshot> begin(StructureOperation.Context context, BeginRequest request);

    /** Query only an operation owned by this exact actor, activation and control ID. */
    StructureOperation.Result<Snapshot> status(StructureOperation.Context context, StatusRequest request);

    /** Idempotently cancel only this operation; never cancel a successor or silently launch. */
    StructureOperation.Result<Snapshot> cancel(StructureOperation.Context context, CancelRequest request);

    enum Phase { PREPARING, EXTRACTING, CLEAR, HOVERING, RELEASED, BLOCKED, CANCELLED, EXPIRED }

    /** Initialization is legal only before the first physics step of this new capture. */
    enum SourceKind { NEW_CAPTURE, EXISTING_STRUCTURE }

    /**
     * Linear limits include gravity compensation and use aggregate solver mass. The live
     * implementation also holds the authenticated source rotation using at most 2 rad/s,
     * 8 rad/s² and min(10,000, maxForce * collision radius) torque; contact admission still
     * checks the complete angular sweep. These bounds never grant pose-reset permission.
     */
    record Limits(double maxLinearSpeed, double maxLinearAcceleration, double maxForce,
                  double maxRetreatDistance, int maxShapeTests) {
        public Limits {
            positive(maxLinearSpeed, 100.0, "maxLinearSpeed");
            positive(maxLinearAcceleration, 1_000.0, "maxLinearAcceleration");
            positive(maxForce, 10_000.0, "maxForce");
            nonnegative(maxRetreatDistance, MAX_RETREAT_DISTANCE, "maxRetreatDistance");
            count(maxShapeTests, MAX_SHAPE_TESTS, "maxShapeTests");
        }
    }

    /** The physical capture selection is immutable and bounded before any host access. */
    record CaptureRequest(UUID controlId, StructureCaptureRequest capture, double liftHeight,
                          int extractionTimeoutTicks, int hoverTicks, Limits limits) {
        public CaptureRequest {
            Objects.requireNonNull(controlId, "controlId");
            Objects.requireNonNull(capture, "capture");
            if (!capture.physical() || capture.positions().isEmpty()
                    || capture.positions().size() > capture.maximumBlocks()) {
                throw new IllegalArgumentException("Extraction requires a nonempty physical capture within budget");
            }
            long minX = Long.MAX_VALUE, minY = Long.MAX_VALUE, minZ = Long.MAX_VALUE;
            long maxX = Long.MIN_VALUE, maxY = Long.MIN_VALUE, maxZ = Long.MIN_VALUE;
            for (var position : capture.positions()) {
                minX = Math.min(minX, position.getX());
                minY = Math.min(minY, position.getY());
                minZ = Math.min(minZ, position.getZ());
                maxX = Math.max(maxX, position.getX());
                maxY = Math.max(maxY, position.getY());
                maxZ = Math.max(maxZ, position.getZ());
            }
            if (maxX - minX + 1 > capture.maximumSpan() || maxY - minY + 1 > capture.maximumSpan()
                    || maxZ - minZ + 1 > capture.maximumSpan()) {
                throw new IllegalArgumentException("Capture source exceeds span budget");
            }
            positive(liftHeight, MAX_LIFT_HEIGHT, "liftHeight");
            count(extractionTimeoutTicks, MAX_DURATION_TICKS, "extractionTimeoutTicks");
            if (hoverTicks < 0 || hoverTicks > MAX_DURATION_TICKS) {
                throw new IllegalArgumentException("Invalid hoverTicks");
            }
            Objects.requireNonNull(limits, "limits");
        }
    }

    /**
     * Display-only pre-capture preview. Digest is SHA-256 over the service's canonical source
     * snapshot, including relevant block/BE data, not merely position names. Implementations
     * without BE evidence must refuse such cells instead of omitting their payload. The actual trusted
     * request, actor binding and source digest remain in the server-owned plan registry.
     */
    record CapturePlan(UUID planId, UUID actorId, Identifier hostDimension, String sourceContentDigest,
                       long terrainRevision, long expiresAtGameTime, CaptureRequest request,
                       List<Vec3> worldPath, List<BlockPos> blockedCells) {
        public CapturePlan {
            Objects.requireNonNull(planId, "planId");
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(hostDimension, "hostDimension");
            Objects.requireNonNull(sourceContentDigest, "sourceContentDigest");
            if (!sourceContentDigest.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Source digest must be lowercase SHA-256");
            }
            revision(terrainRevision, "terrainRevision");
            revision(expiresAtGameTime, "expiresAtGameTime");
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(worldPath, "worldPath");
            Objects.requireNonNull(blockedCells, "blockedCells");
            if (worldPath.size() > MAX_PATH_POINTS || blockedCells.size() > MAX_BLOCKED_CELLS) {
                throw new IllegalArgumentException("Capture preview exceeds budget");
            }
            worldPath = worldPath.stream().map(point ->
                    finiteVector(point, 30_000_000.0, "worldPath point")).toList();
            blockedCells = blockedCells.stream().map(position ->
                    Objects.requireNonNull(position, "blocked cell").immutable()).toList();
        }
    }

    /** IDs select server records; no caller-provided pose, digest or result can manufacture a binding. */
    record BindCaptureRequest(UUID controlId, UUID planId, UUID captureOperationId) {
        public BindCaptureRequest {
            Objects.requireNonNull(controlId, "controlId");
            Objects.requireNonNull(planId, "planId");
            Objects.requireNonNull(captureOperationId, "captureOperationId");
        }
    }

    /**
     * Source pose and mass center are observations to verify, never instructions to reset an
     * existing body. liftHeight is measured from that source center; H supplies 3/5/7 blocks.
     * Extraction timeout and hover duration are independent; hover starts only after arrival.
     */
    record PlanRequest(UUID controlId, SourceKind sourceKind, StructurePose sourcePose,
                       Vec3 localCenterOfMass, double liftHeight, int extractionTimeoutTicks,
                       int hoverTicks, Limits limits) {
        public PlanRequest {
            Objects.requireNonNull(controlId, "controlId");
            Objects.requireNonNull(sourceKind, "sourceKind");
            Objects.requireNonNull(sourcePose, "sourcePose");
            finiteVector(sourcePose.worldOrigin(), 30_000_000.0, "sourcePose.worldOrigin");
            localCenterOfMass = finiteVector(localCenterOfMass, 1_000_000.0, "localCenterOfMass");
            positive(liftHeight, MAX_LIFT_HEIGHT, "liftHeight");
            count(extractionTimeoutTicks, MAX_DURATION_TICKS, "extractionTimeoutTicks");
            if (hoverTicks < 0 || hoverTicks > MAX_DURATION_TICKS) {
                throw new IllegalArgumentException("hoverTicks outside [0, " + MAX_DURATION_TICKS + "]");
            }
            Objects.requireNonNull(limits, "limits");
        }
    }

    /**
     * Preview data only. begin resolves planId from server state instead of trusting a caller's
     * reconstructed record. World path is source-relative in purpose but contains host positions.
     * Blocked cells use host coordinates, never hidden plot addresses.
     */
    record Plan(UUID planId, StructureRef reference, UUID runtimeId, long contentRevision,
                long poseRevision, long terrainRevision, long expiresAtGameTime,
                PlanRequest request, List<Vec3> worldPath, List<BlockPos> blockedCells) {
        public Plan {
            Objects.requireNonNull(planId, "planId");
            Objects.requireNonNull(reference, "reference");
            Objects.requireNonNull(runtimeId, "runtimeId");
            revision(contentRevision, "contentRevision");
            revision(poseRevision, "poseRevision");
            revision(terrainRevision, "terrainRevision");
            revision(expiresAtGameTime, "expiresAtGameTime");
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(worldPath, "worldPath");
            Objects.requireNonNull(blockedCells, "blockedCells");
            if (worldPath.size() > MAX_PATH_POINTS || blockedCells.size() > MAX_BLOCKED_CELLS) {
                throw new IllegalArgumentException("Extraction plan exceeds preview budget");
            }
            worldPath = worldPath.stream().map(point ->
                    finiteVector(point, 30_000_000.0, "worldPath point")).toList();
            blockedCells = blockedCells.stream().map(position ->
                    Objects.requireNonNull(position, "blocked cell").immutable()).toList();
        }
    }

    record BeginRequest(UUID controlId, UUID planId) {
        public BeginRequest {
            Objects.requireNonNull(controlId, "controlId");
            Objects.requireNonNull(planId, "planId");
        }
    }

    record StatusRequest(UUID controlId, UUID operationId) {
        public StatusRequest {
            Objects.requireNonNull(controlId, "controlId");
            Objects.requireNonNull(operationId, "operationId");
        }
    }

    record CancelRequest(UUID controlId, UUID operationId) {
        public CancelRequest {
            Objects.requireNonNull(controlId, "controlId");
            Objects.requireNonNull(operationId, "operationId");
        }
    }

    /** A blocked or expired extraction must not report its missing hover time as completed. */
    record Snapshot(UUID operationId, UUID controlId, Phase phase, double achievedRise,
                    int stableHoverTicks, long elapsedTicks, Optional<Vec3> obstructionPoint) {
        public Snapshot {
            Objects.requireNonNull(operationId, "operationId");
            Objects.requireNonNull(controlId, "controlId");
            Objects.requireNonNull(phase, "phase");
            if (!Double.isFinite(achievedRise)) throw new IllegalArgumentException("achievedRise must be finite");
            if (stableHoverTicks < 0 || stableHoverTicks > MAX_DURATION_TICKS) {
                throw new IllegalArgumentException("Invalid stableHoverTicks");
            }
            revision(elapsedTicks, "elapsedTicks");
            obstructionPoint = Objects.requireNonNull(obstructionPoint, "obstructionPoint")
                    .map(point -> finiteVector(point, 30_000_000.0, "obstructionPoint"));
        }
    }

    private static Vec3 finiteVector(Vec3 value, double maximum, String name) {
        Objects.requireNonNull(value, name);
        if (!Double.isFinite(value.x) || !Double.isFinite(value.y) || !Double.isFinite(value.z)
                || Math.abs(value.x) > maximum || Math.abs(value.y) > maximum || Math.abs(value.z) > maximum) {
            throw new IllegalArgumentException(name + " must be finite and in bounds");
        }
        return new Vec3(value.x, value.y, value.z);
    }

    private static void positive(double value, double maximum, String name) {
        nonnegative(value, maximum, name);
        if (value == 0.0) throw new IllegalArgumentException(name + " must be positive");
    }

    private static void nonnegative(double value, double maximum, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > maximum) {
            throw new IllegalArgumentException(name + " outside [0, " + maximum + "]");
        }
    }

    private static void count(int value, int maximum, String name) {
        if (value <= 0 || value > maximum) throw new IllegalArgumentException(name + " outside (0, " + maximum + "]");
    }

    private static void revision(long value, String name) {
        if (value < 0) throw new IllegalArgumentException(name + " cannot be negative");
    }
}
