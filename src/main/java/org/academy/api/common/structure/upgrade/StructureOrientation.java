package org.academy.api.common.structure.upgrade;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Planned backend-neutral angular control. Calls require an already owned exclusive control ID;
 * none acquire or steal a lease. One angular command may coexist with that lease's translation
 * command, but rotate/reset/spin replace one another with an explicit operation receipt.
 * Collision, terrain coverage and safe swept paths remain mandatory, including for forced reset.
 */
public interface StructureOrientation {
    int MAX_DURATION_TICKS = 1_200;
    int MAX_SHAPE_TESTS = 262_144;
    double MAX_ANGULAR_SPEED = 100.0;
    double MAX_ANGULAR_ACCELERATION = 1_000.0;
    double MAX_TORQUE = 10_000.0;

    /** Absolute rotation, matching the existing structure/rotate node's signed axis-angle semantics. */
    StructureOperation.Result<Snapshot> rotateTo(StructureOperation.Context context, RotateRequest request);

    /** Resolve a trusted baseline, brake old angular motion and follow a safe rotation path. */
    StructureOperation.Result<Snapshot> reset(StructureOperation.Context context, ResetRequest request);

    /** Maintain bounded angular speed for a finite duration in the explicitly selected frame. */
    StructureOperation.Result<Snapshot> spin(StructureOperation.Context context, SpinRequest request);

    /** Stop only the specified operation; a stale operation cannot stop its replacement. */
    StructureOperation.Result<Snapshot> stop(StructureOperation.Context context, StopRequest request);

    /** Does not disclose another actor's operation or reactivate an unloaded structure. */
    StructureOperation.Result<Snapshot> status(StructureOperation.Context context, StatusRequest request);

    enum AxisFrame { WORLD, LOCAL }
    enum Kind { ROTATE_TO, RESET, SPIN }
    enum Phase { PREPARING, CONTROLLING, BRAKING, COMPLETED, BLOCKED, STOPPED, EXPIRED }
    enum ResetMode { CAPTURE_BASELINE, WORLD_UP_KEEP_YAW, NEAREST_GRID_YAW }
    enum MissingBaselinePolicy { REJECT, USE_WORLD_GRID }
    enum StopMode { BRAKE, RELEASE_INERTIA }

    /** Reported by the backend, never accepted as caller-supplied provenance. */
    enum BaselineSource { CAPTURED_BLOCK_FRAME, BACKEND_LOCAL_GRID, WORLD_UP, WORLD_GRID, NOT_APPLICABLE }

    /**
     * Speeds are rad/s, acceleration rad/s² and torque uses solver mass units. Operation lifetime
     * is bounded by timeoutTicks. maxRetreatDistance=0 preserves the exact world mass center;
     * any nonzero allowance still requires a separately validated collision-free path.
     */
    record Limits(double maxAngularSpeed, double maxAngularAcceleration, double maxTorque,
                  int timeoutTicks, double angleTolerance, double angularSpeedTolerance,
                  double maxRetreatDistance, int maxShapeTests) {
        public Limits {
            positive(maxAngularSpeed, MAX_ANGULAR_SPEED, "maxAngularSpeed");
            positive(maxAngularAcceleration, MAX_ANGULAR_ACCELERATION, "maxAngularAcceleration");
            positive(maxTorque, MAX_TORQUE, "maxTorque");
            duration(timeoutTicks, "timeoutTicks");
            positive(angleTolerance, Math.PI, "angleTolerance");
            positive(angularSpeedTolerance, maxAngularSpeed, "angularSpeedTolerance");
            nonnegative(maxRetreatDistance, StructureExtraction.MAX_RETREAT_DISTANCE, "maxRetreatDistance");
            if (maxShapeTests <= 0 || maxShapeTests > MAX_SHAPE_TESTS) {
                throw new IllegalArgumentException("Invalid maxShapeTests");
            }
        }
    }

    /** targetRotation must have zero translation; it never requests a position teleport. */
    record RotateRequest(UUID controlId, StructurePose targetRotation, Limits limits) {
        public RotateRequest {
            Objects.requireNonNull(controlId, "controlId");
            requireRotation(targetRotation);
            Objects.requireNonNull(limits, "limits");
        }
    }

    /**
     * The capture/local-grid basis is resolved from server-owned metadata. A missing basis is
     * rejected unless USE_WORLD_GRID was explicitly requested; current tilt is not a baseline.
     */
    record ResetRequest(UUID controlId, ResetMode mode, MissingBaselinePolicy missingBaselinePolicy,
                        Limits limits) {
        public ResetRequest {
            Objects.requireNonNull(controlId, "controlId");
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(missingBaselinePolicy, "missingBaselinePolicy");
            Objects.requireNonNull(limits, "limits");
        }
    }

    /** A signed angularSpeed is allowed; axis is copied and normalized without losing its sign. */
    record SpinRequest(UUID controlId, Vec3 axis, AxisFrame frame, double angularSpeed,
                       int durationTicks, StopMode finishMode, Limits limits) {
        public SpinRequest {
            Objects.requireNonNull(controlId, "controlId");
            Objects.requireNonNull(frame, "frame");
            Objects.requireNonNull(finishMode, "finishMode");
            Objects.requireNonNull(limits, "limits");
            axis = unitAxis(axis);
            if (!Double.isFinite(angularSpeed) || angularSpeed == 0.0
                    || Math.abs(angularSpeed) > limits.maxAngularSpeed()) {
                throw new IllegalArgumentException("angularSpeed must be nonzero and within the command limit");
            }
            duration(durationTicks, "durationTicks");
            if (durationTicks > limits.timeoutTicks()) {
                throw new IllegalArgumentException("durationTicks exceeds timeoutTicks");
            }
        }
    }

    record StopRequest(UUID controlId, UUID operationId, StopMode mode, Limits limits) {
        public StopRequest {
            Objects.requireNonNull(controlId, "controlId");
            Objects.requireNonNull(operationId, "operationId");
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(limits, "limits");
        }
    }

    record StatusRequest(UUID controlId, UUID operationId) {
        public StatusRequest {
            Objects.requireNonNull(controlId, "controlId");
            Objects.requireNonNull(operationId, "operationId");
        }
    }

    /** Read-only server-resolved plan; spin has no fixed target rotation. */
    record Plan(Kind kind, BaselineSource baselineSource, Optional<StructurePose> targetRotation,
                Limits limits, long poseRevision, long terrainRevision) {
        public Plan {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(baselineSource, "baselineSource");
            targetRotation = Objects.requireNonNull(targetRotation, "targetRotation");
            targetRotation.ifPresent(StructureOrientation::requireRotation);
            if (kind == Kind.SPIN && targetRotation.isPresent()
                    || kind != Kind.SPIN && targetRotation.isEmpty()) {
                throw new IllegalArgumentException("Target rotation does not match orientation kind");
            }
            if (kind == Kind.RESET && baselineSource == BaselineSource.NOT_APPLICABLE) {
                throw new IllegalArgumentException("Reset requires a resolved baseline");
            }
            Objects.requireNonNull(limits, "limits");
            if (poseRevision < 0 || terrainRevision < 0) throw new IllegalArgumentException("Negative revision");
        }
    }

    /** Completion requires both angular error and angular velocity tolerances, never submission alone. */
    record Snapshot(UUID operationId, UUID controlId, Phase phase, Plan plan,
                    double angleError, double angularSpeed, long elapsedTicks) {
        public Snapshot {
            Objects.requireNonNull(operationId, "operationId");
            Objects.requireNonNull(controlId, "controlId");
            Objects.requireNonNull(phase, "phase");
            Objects.requireNonNull(plan, "plan");
            nonnegative(angleError, Math.PI, "angleError");
            if (!Double.isFinite(angularSpeed)) throw new IllegalArgumentException("angularSpeed must be finite");
            if (elapsedTicks < 0) throw new IllegalArgumentException("elapsedTicks cannot be negative");
        }
    }

    private static void requireRotation(StructurePose value) {
        Objects.requireNonNull(value, "targetRotation");
        if (value.worldOrigin().x != 0.0 || value.worldOrigin().y != 0.0 || value.worldOrigin().z != 0.0) {
            throw new IllegalArgumentException("targetRotation must not contain a translation");
        }
    }

    private static Vec3 unitAxis(Vec3 axis) {
        Objects.requireNonNull(axis, "axis");
        if (!Double.isFinite(axis.x) || !Double.isFinite(axis.y) || !Double.isFinite(axis.z)) {
            throw new IllegalArgumentException("axis must be finite");
        }
        double scale = Math.max(Math.abs(axis.x), Math.max(Math.abs(axis.y), Math.abs(axis.z)));
        if (scale == 0.0) throw new IllegalArgumentException("axis must be nonzero");
        double x = axis.x / scale, y = axis.y / scale, z = axis.z / scale;
        double length = Math.sqrt(x * x + y * y + z * z);
        return new Vec3(x / length, y / length, z / length);
    }

    private static void duration(int ticks, String name) {
        if (ticks <= 0 || ticks > MAX_DURATION_TICKS) throw new IllegalArgumentException(name + " outside duration budget");
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
}
