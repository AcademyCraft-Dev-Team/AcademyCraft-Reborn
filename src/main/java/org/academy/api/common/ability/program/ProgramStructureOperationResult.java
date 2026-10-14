package org.academy.api.common.ability.program;

import org.academy.api.common.structure.StructureRef;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Machine-readable outcome of one structure action node. Programs branch on {@link #status()}
 * and {@link #detail()} instead of parsing log text; {@link #reference()} carries the structure
 * identity only when a committed content transaction published one.
 */
public record ProgramStructureOperationResult(
        Status status,
        String detail,
        @Nullable StructureRef reference
) {
    public ProgramStructureOperationResult {
        Objects.requireNonNull(status, "status");
        detail = detail == null ? "" : detail;
    }

    public static ProgramStructureOperationResult completed(String detail) {
        return new ProgramStructureOperationResult(Status.COMPLETED, detail, null);
    }

    public static ProgramStructureOperationResult completed(String detail, StructureRef reference) {
        return new ProgramStructureOperationResult(
                Status.COMPLETED, detail, Objects.requireNonNull(reference, "reference"));
    }

    public static ProgramStructureOperationResult pending(String detail) {
        return new ProgramStructureOperationResult(Status.PENDING, detail, null);
    }

    public static ProgramStructureOperationResult rejected(String detail) {
        return new ProgramStructureOperationResult(Status.REJECTED, detail, null);
    }

    public static ProgramStructureOperationResult unavailable(String detail) {
        return new ProgramStructureOperationResult(Status.UNAVAILABLE, detail, null);
    }

    public enum Status {
        COMPLETED,
        PENDING,
        REJECTED,
        UNAVAILABLE
    }
}
