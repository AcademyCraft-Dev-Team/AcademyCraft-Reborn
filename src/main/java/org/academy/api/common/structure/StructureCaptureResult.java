package org.academy.api.common.structure;

import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Result of a structure capture transaction. PENDING means the staged commit is still in
 * flight; poll by transaction id. Rejections name the first problem position and an explicit
 * detail reason. RECOVERY_REQUIRED retains persistent journal evidence instead of turning an
 * unknown outcome into a missing structure.
 */
public record StructureCaptureResult(
        UUID transactionId,
        Status status,
        Optional<StructureRef> structure,
        Optional<BlockPos> problemPosition,
        int affectedBlocks,
        String detail
) {
    public StructureCaptureResult {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(problemPosition, "problemPosition");
        detail = detail == null ? "" : detail;
        affectedBlocks = Math.max(0, affectedBlocks);
    }

    public boolean succeeded() {
        return status == Status.SUCCESS;
    }

    public boolean pending() {
        return status == Status.PENDING;
    }

    public static StructureCaptureResult pending(UUID transactionId, StructureRef structure, String detail) {
        return new StructureCaptureResult(transactionId, Status.PENDING, Optional.of(structure),
                Optional.empty(), 0, detail);
    }

    public static StructureCaptureResult success(UUID transactionId, StructureRef structure, int affectedBlocks) {
        return new StructureCaptureResult(transactionId, Status.SUCCESS, Optional.of(structure),
                Optional.empty(), affectedBlocks, "committed");
    }

    public static StructureCaptureResult failure(UUID transactionId, Status status, String detail) {
        return failure(transactionId, status, null, detail);
    }

    public static StructureCaptureResult failure(UUID transactionId, Status status, @Nullable BlockPos problemPosition,
                                                 String detail) {
        if (status == Status.SUCCESS || status == Status.PENDING) {
            throw new IllegalArgumentException(status + " requires a live transaction");
        }
        return new StructureCaptureResult(transactionId, status, Optional.empty(),
                Optional.ofNullable(problemPosition), 0, detail);
    }

    public enum Status {
        PENDING,
        SUCCESS,
        EMPTY,
        TOO_LARGE,
        SPAN_TOO_LARGE,
        OUT_OF_WORLD,
        UNLOADED,
        /** A source cell is outside the admitted plain-block scope; the detail names why. */
        REJECTED,
        PERMISSION_DENIED,
        BACKEND_UNAVAILABLE,
        JOURNAL_WRITE_FAILED,
        SOURCE_CHANGED,
        TARGET_WRITE_FAILED,
        TIMEOUT,
        RECOVERY_REQUIRED
    }
}
