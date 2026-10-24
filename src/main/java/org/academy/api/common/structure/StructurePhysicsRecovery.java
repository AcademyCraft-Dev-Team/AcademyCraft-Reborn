package org.academy.api.common.structure;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Result of rebuilding an entire failed legacy region without recapturing its blocks. */
public record StructurePhysicsRecovery(Status status, @Nullable StructurePhysicsRegion region, String diagnostic) {
    public StructurePhysicsRecovery {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(diagnostic, "diagnostic");
        if ((status == Status.RECOVERED) != (region != null)) {
            throw new IllegalArgumentException("Only a recovered result may contain a region");
        }
    }

    public enum Status {
        RECOVERED,
        /** Retry after ordinary storage/chunk activation; this operation never forces chunks. */
        WAITING,
        /** The original checkpoint is retained; resolve the diagnostic before trying again. */
        REJECTED
    }
}
