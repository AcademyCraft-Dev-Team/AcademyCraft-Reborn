package org.academy.api.common.ability.program;

import org.academy.api.common.structure.upgrade.StructureOrientation;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Server-owned, asynchronous attitude actions shared by the three structure-capable categories.
 * A command uses an already issued control token; it never acquires or transfers ownership.
 * Implementations derive physical limits and permissions from server state and retain receipts
 * until read. Ending the program cancels only its pending angular commands, retaining the
 * pre-existing translation lease and its established lifetime.
 */
public interface ProgramStructureOrientationRuntime {
    /** Absolute signed axis-angle rotation; the legacy optional pose port stays optional. */
    default Operation rotateOrientation(ProgramStructureControl control, @Nullable ProgramStructurePose pose) {
        Objects.requireNonNull(control, "control");
        return Operation.unavailable("structure_orientation_unavailable");
    }

    /** Submit once. A PENDING receipt is polled through {@link #orientationStatus}. */
    default Operation resetOrientation(ProgramStructureControl control, ResetRequest request) {
        Objects.requireNonNull(control, "control");
        Objects.requireNonNull(request, "request");
        return Operation.unavailable("structure_orientation_unavailable");
    }

    /** Maintain the signed angular speed for a bounded duration, then apply the finish mode. */
    default Operation spinStructure(ProgramStructureControl control, SpinRequest request) {
        Objects.requireNonNull(control, "control");
        Objects.requireNonNull(request, "request");
        return Operation.unavailable("structure_orientation_unavailable");
    }

    /** Poll without resubmitting or renewing a command; another token's receipt is rejected. */
    default Operation orientationStatus(ProgramStructureControl control, UUID operationId) {
        Objects.requireNonNull(control, "control");
        Objects.requireNonNull(operationId, "operationId");
        return Operation.unavailable("structure_orientation_unavailable");
    }

    /** Called on completion, failure, cancellation or scheduler rejection; must be idempotent. */
    default void closeOrientationCommands() {
    }

    record ResetRequest(StructureOrientation.ResetMode mode,
                        StructureOrientation.MissingBaselinePolicy missingBaselinePolicy,
                        int timeoutTicks) {
        public ResetRequest {
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(missingBaselinePolicy, "missingBaselinePolicy");
            requireDuration(timeoutTicks);
        }
    }

    /** Axis frame and speed units are explicit; angularSpeed is signed radians per second. */
    record SpinRequest(ProgramDirection axis, StructureOrientation.AxisFrame frame,
                       double angularSpeed, int durationTicks, StructureOrientation.StopMode finishMode) {
        public SpinRequest {
            Objects.requireNonNull(axis, "axis");
            Objects.requireNonNull(frame, "frame");
            Objects.requireNonNull(finishMode, "finishMode");
            // Validate at this public boundary even if a direction came from an external executor.
            if (!Double.isFinite(axis.x()) || !Double.isFinite(axis.y()) || !Double.isFinite(axis.z())
                    || Math.max(Math.abs(axis.x()), Math.max(Math.abs(axis.y()), Math.abs(axis.z()))) == 0.0) {
                throw new IllegalArgumentException("Spin axis must be finite and nonzero");
            }
            if (!Double.isFinite(angularSpeed) || angularSpeed == 0.0
                    || Math.abs(angularSpeed) > StructureOrientation.MAX_ANGULAR_SPEED) {
                throw new IllegalArgumentException("Spin speed is outside the angular budget");
            }
            requireDuration(durationTicks);
        }
    }

    /** Runtime-only receipt. Submission alone is never reported as COMPLETED. */
    record Operation(ProgramStructureOperationResult result, @Nullable UUID operationId) {
        public Operation {
            Objects.requireNonNull(result, "result");
            if (result.status() == ProgramStructureOperationResult.Status.PENDING && operationId == null) {
                throw new IllegalArgumentException("A pending orientation command requires an operation ID");
            }
        }

        public static Operation pending(UUID operationId, String detail) {
            return new Operation(ProgramStructureOperationResult.pending(detail),
                    Objects.requireNonNull(operationId, "operationId"));
        }

        public static Operation completed(String detail) {
            return new Operation(ProgramStructureOperationResult.completed(detail), null);
        }

        public static Operation rejected(String detail) {
            return new Operation(ProgramStructureOperationResult.rejected(detail), null);
        }

        public static Operation blocked(String detail) {
            return rejected("orientation_blocked:" + Objects.requireNonNull(detail, "detail"));
        }

        public static Operation unavailable(String detail) {
            return new Operation(ProgramStructureOperationResult.unavailable(detail), null);
        }

        /** Compatibility for synchronous extensions, which cannot supply an asynchronous receipt. */
        public static Operation fromLegacy(ProgramStructureOperationResult result) {
            Objects.requireNonNull(result, "result");
            return result.status() == ProgramStructureOperationResult.Status.PENDING
                    ? rejected("orientation_receipt_missing") : new Operation(result, null);
        }
    }

    private static void requireDuration(int ticks) {
        if (ticks <= 0 || ticks > StructureOrientation.MAX_DURATION_TICKS) {
            throw new IllegalArgumentException("Orientation duration is outside the tick budget");
        }
    }
}
